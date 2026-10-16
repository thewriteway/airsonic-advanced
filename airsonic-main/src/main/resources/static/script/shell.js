/*
 * Application shell: hosts the Airsonic pages in regions of a single document.
 *
 * A region fetches a page with the X-Airsonic-Shell request header, which makes the server leave
 * out everything the shell already has (common head, libraries), so the response only carries the
 * page's own scripts and styles and its body. The region unmounts the page it showed (see page.js),
 * moves the new body (with the class, style and data attributes of the page's <body>) into the
 * region, runs the page's scripts in document order (libraries the shell already has are not
 * loaded twice) and mounts the new page.
 *
 * Links and forms that used to navigate a frame now load the corresponding region, and the main
 * region keeps the browser history and address bar in sync. The frame references used by the
 * pages (top.main.location, parent.frames.left.location, top.main.$, ...) are emulated by
 * window.main, window.left, window.right and window.upper, so the pages keep working unchanged.
 *
 * Expects window.ShellConfig = {base: context path with trailing slash, pages: [page names]}, and
 * elements with data-shell-region (region name) and data-shell-src (initial URL, if any).
 */
/* global Page */
(function () {
    "use strict";

    var config = window.ShellConfig;
    // scripts the shell runs on behalf of a page need the nonce of the Content-Security-Policy
    var nonce = (document.currentScript && document.currentScript.nonce) || "";
    var pages = new Set(config.pages);
    // a Map, so that frame names taken from the pages (target="...") never hit Object.prototype
    var regions = new Map();
    var hiddenFrame = null;

    function resolve(url, base) {
        return new URL(url, base || document.baseURI);
    }

    /** Whether a URL addresses one of the pages that are displayed in a region. */
    function isPage(url) {
        var u = resolve(url);
        var basePath = resolve(config.base).pathname;
        if (u.origin !== location.origin || u.pathname.indexOf(basePath) !== 0) {
            return false;
        }
        var name = u.pathname.substring(basePath.length).replace(/\.view$/, "");
        return pages.has(name);
    }

    function isLoginPage(url) {
        return resolve(url).pathname === resolve(config.base + "login").pathname;
    }

    function hasScript(src) {
        return Array.prototype.some.call(document.scripts, function (s) {
            return s.src === src;
        });
    }

    function hasStylesheet(href) {
        return Array.prototype.some.call(document.querySelectorAll('link[rel~="stylesheet"]'), function (l) {
            return l.href === href;
        });
    }

    /** Stops downloading a response that will not be used. */
    function discardBody(response) {
        if (response.body) {
            response.body.cancel().catch(function () {});
        }
    }

    /** Loads a URL the way the old frames did for anything that is not a page, e.g. downloads and m3u files. */
    function openInHiddenFrame(url) {
        hiddenFrame.src = resolve(url).href;
    }

    /** Runs a script parsed by DOMParser (such scripts are inert). Resolves when the script has run. */
    function runScript(parsed, baseUrl) {
        var type = parsed.getAttribute("type");
        if (type && !/^(text|application)\/(java|ecma)script$|^module$/i.test(type)) {
            return Promise.resolve();
        }
        var script = document.createElement("script");
        script.nonce = nonce;
        if (type) {
            script.type = type;
        }
        if (!parsed.hasAttribute("src")) {
            script.textContent = parsed.textContent;
            if (parsed.ownerDocument === document) {
                // body script: run in place, like the parser would have done
                parsed.replaceWith(script);
            } else {
                document.head.appendChild(script);
                script.remove();
            }
            return Promise.resolve();
        }
        // libraries go to the head so they stay loaded when the region shows another page
        var src = resolve(parsed.getAttribute("src"), baseUrl).href;
        parsed.remove();
        if (hasScript(src)) {
            return Promise.resolve();
        }
        return new Promise(function (resolveLoad) {
            script.src = src;
            script.async = false;
            script.onload = script.onerror = function () {
                resolveLoad();
            };
            document.head.appendChild(script);
        });
    }

    function Region(name, element, options) {
        this.name = name;
        this.element = element;
        this.content = element.querySelector(".shell-content");
        this.history = options && options.history;
        this.url = null;
        this.styles = [];
        this.sequence = 0;
        this.request = null;
    }

    /**
     * Navigates the region to a URL, as assigning frame.location did. URLs that are not pages
     * (downloads, playlists for external players) go to the hidden frame instead.
     */
    Region.prototype.navigate = function (url, options) {
        var target = resolve(url, this.url);
        if (!isPage(target)) {
            openInHiddenFrame(target);
            return Promise.resolve();
        }
        return this.load(target.href, options);
    };

    Region.prototype.reload = function () {
        return this.url ? this.load(this.url, {history: "none"}) : Promise.resolve();
    };

    /**
     * @param url the URL to load
     * @param options method and body for form submissions, history: "push" (default for the
     *        history region), "replace" or "none"
     */
    Region.prototype.load = function (url, options) {
        options = options || {};
        var sequence = ++this.sequence;
        var hash = resolve(url).hash;
        this.element.setAttribute("aria-busy", "true");

        // a newer navigation replaces the one still loading, so stop downloading the old page
        if (this.request) {
            this.request.abort();
        }
        var request = new AbortController();
        this.request = request;

        return fetch(url, {
            method: options.method || "GET",
            body: options.body,
            credentials: "same-origin",
            headers: {"X-Airsonic-Shell": this.name},
            signal: request.signal
        }).then((response) => {
            if (sequence !== this.sequence) {
                discardBody(response);
                return null;
            }
            if (isLoginPage(response.url)) {
                // session expired: leave the shell
                window.location.assign(response.url);
                return null;
            }
            if ((response.headers.get("Content-Type") || "").indexOf("text/html") === -1) {
                // the hidden frame requests it again, so don't download it twice
                discardBody(response);
                if (!options.method || options.method === "GET") {
                    openInHiddenFrame(url);
                }
                return null;
            }
            return response.text().then((html) => {
                if (sequence === this.sequence) {
                    var finalUrl = resolve(response.url);
                    if (!finalUrl.hash && hash && response.url.split("#")[0] === url.split("#")[0]) {
                        finalUrl.hash = hash;
                    }
                    return this.render(html, finalUrl.href, options);
                }
            });
        }).catch((error) => {
            if (error.name !== "AbortError") {
                console.error("Could not load " + url + " into the " + this.name + " region", error);
            }
        }).finally(() => {
            if (this.request === request) {
                this.request = null;
            }
            if (sequence === this.sequence) {
                this.element.removeAttribute("aria-busy");
            }
        });
    };

    Region.prototype.render = function (html, url, options) {
        var doc = new DOMParser().parseFromString(html, "text/html");
        var sequence = this.sequence;
        this.url = url;

        if (this.history) {
            var historyMode = options.history || "push";
            var state = {shell: url};
            if (historyMode === "push" && url !== location.href) {
                history.pushState(state, "", url);
            } else if (historyMode !== "none") {
                history.replaceState(state, "", url);
            }
        }

        // Stylesheets are global and stay loaded; inline styles belong to the page shown.
        doc.querySelectorAll('link[rel~="stylesheet"]').forEach(function (link) {
            var href = resolve(link.getAttribute("href"), url).href;
            link.remove();
            if (!hasStylesheet(href)) {
                var copy = document.createElement("link");
                copy.rel = "stylesheet";
                copy.href = href;
                document.head.appendChild(copy);
            }
        });
        this.styles.forEach(function (style) {
            style.remove();
        });
        this.styles = Array.prototype.map.call(doc.head.querySelectorAll("style"), (parsed) => {
            var style = document.createElement("style");
            style.textContent = parsed.textContent;
            style.dataset.shellRegion = this.name;
            document.head.appendChild(style);
            return style;
        });

        // Release the previous page (timers, subscriptions, jQuery data), then show the new body.
        Page.unmount(this.content);
        if (window.jQuery) {
            window.jQuery(this.content).empty();
        }
        this.content.className = "shell-content " + doc.body.className;
        this.content.setAttribute("style", doc.body.getAttribute("style") || "");
        // the data attributes of the page's body (data-page is set once its scripts have run)
        Array.prototype.slice.call(this.content.attributes).forEach((attribute) => {
            if (attribute.name.indexOf("data-") === 0) {
                this.content.removeAttribute(attribute.name);
            }
        });
        Array.prototype.forEach.call(doc.body.attributes, (attribute) => {
            if (attribute.name.indexOf("data-") === 0 && attribute.name !== "data-page") {
                this.content.setAttribute(attribute.name, attribute.value);
            }
        });
        // copy first: adopting a node removes it from the live childNodes list
        this.content.replaceChildren.apply(this.content, Array.from(doc.body.childNodes).map(function (node) {
            return document.adoptNode(node);
        }));

        var anchor = new URL(url).hash.substring(1);
        var anchorTarget = anchor && (document.getElementById(anchor) || document.getElementsByName(anchor)[0]);
        if (anchorTarget) {
            anchorTarget.scrollIntoView();
        } else {
            this.element.scrollTop = 0;
        }

        var scripts = Array.prototype.slice.call(doc.head.querySelectorAll("script"))
            .concat(Array.prototype.slice.call(this.content.querySelectorAll("script")));
        var pageName = doc.body.getAttribute("data-page");
        return scripts.reduce(function (previous, script) {
            return previous.then(function () {
                return runScript(script, url);
            });
        }, Promise.resolve()).then(() => {
            if (sequence !== this.sequence) {
                // another page was requested meanwhile, it will be mounted instead
                return;
            }
            if (pageName) {
                this.content.setAttribute("data-page", pageName);
                Page.mount(this.content);
            }
            this.element.dispatchEvent(new CustomEvent("shell:load", {detail: {url: url}}));
        });
    };

    /** Emulates the window object of the frame a region replaces. */
    function frameProxy(region) {
        var location = {
            get href() {
                return region.url || "about:blank";
            },
            set href(url) {
                region.navigate(String(url));
            },
            get pathname() {
                return resolve(this.href).pathname;
            },
            get search() {
                return resolve(this.href).search;
            },
            get hash() {
                return resolve(this.href).hash;
            },
            assign: function (url) {
                region.navigate(String(url));
            },
            replace: function (url) {
                region.navigate(String(url), {history: "replace"});
            },
            reload: function () {
                region.reload();
            },
            toString: function () {
                return this.href;
            }
        };
        return {
            get location() {
                return location;
            },
            set location(url) {
                region.navigate(String(url));
            },
            get document() {
                return document;
            },
            get $() {
                return window.jQuery;
            },
            get jQuery() {
                return window.jQuery;
            },
            region: region
        };
    }

    /** The region a link or form navigates: its target frame, else the region it is in, else main for pages. */
    function targetRegion(element, target, url) {
        if (target && target !== "_self") {
            return regions.get(target) || null;
        }
        var container = element.closest("[data-shell-region]");
        if (container) {
            return regions.get(container.dataset.shellRegion) || null;
        }
        return isPage(url) ? regions.get("main") : null;
    }

    function onClick(event) {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
        }
        var link = event.target.closest && event.target.closest("a[href]");
        if (!link || link.hasAttribute("download")) {
            return;
        }
        var href = link.getAttribute("href");
        if (href.charAt(0) === "#" || /^javascript:/i.test(href) || resolve(link.href).origin !== location.origin) {
            return;
        }
        var target = link.getAttribute("target");
        if (target === "hidden") {
            // the hidden frame of the frameset
            event.preventDefault();
            openInHiddenFrame(link.href);
            return;
        }
        var region = targetRegion(link, target, link.href);
        if (!region) {
            return;
        }
        if (!isPage(link.href)) {
            // a link to a download in a region: only intercept when it names a frame that no longer exists
            if (target && regions.has(target)) {
                event.preventDefault();
                openInHiddenFrame(link.href);
            }
            return;
        }
        event.preventDefault();
        region.navigate(link.href);
    }

    function formRegion(form, submitter) {
        var target = (submitter && submitter.getAttribute("formtarget")) || form.getAttribute("target");
        var action = (submitter && submitter.hasAttribute("formaction")) ? submitter.formAction : form.action;
        var method = ((submitter && submitter.getAttribute("formmethod")) || form.getAttribute("method") || "get").toLowerCase();
        if (method === "dialog") {
            // closes a <dialog>, handled by the browser
            return null;
        }
        var url = resolve(action);
        var sameOrigin = url.origin === location.origin;
        var authentication = isLoginPage(url) || /\/logout$/.test(url.pathname);
        if (!sameOrigin || authentication || (method === "get" && !isPage(url))) {
            return null;
        }
        var region = targetRegion(form, target, url.href);
        return region && {region: region, url: url, method: method};
    }

    function submitForm(form, submitter) {
        var submission = formRegion(form, submitter);
        if (!submission) {
            return false;
        }
        var data = submitter ? new FormData(form, submitter) : new FormData(form);
        if (submission.method === "get") {
            submission.url.search = new URLSearchParams(data).toString();
            submission.region.navigate(submission.url.href);
        } else {
            var enctype = (submitter && submitter.getAttribute("formenctype")) || form.enctype;
            submission.region.load(submission.url.href, {
                method: "POST",
                body: enctype === "multipart/form-data" ? data : new URLSearchParams(data)
            });
        }
        return true;
    }

    function onSubmit(event) {
        if (!event.defaultPrevented && submitForm(event.target, event.submitter)) {
            event.preventDefault();
        }
    }

    function onPopState(event) {
        var main = regions.get("main");
        if (event.state && event.state.shell && event.state.shell !== main.url) {
            main.load(event.state.shell, {history: "none"});
        }
    }

    /** Lets buttons styled as links use the link color of the selected theme. */
    function exposeLinkColor() {
        var link = document.createElement("a");
        link.href = config.base;
        link.hidden = true;
        document.body.appendChild(link);
        document.documentElement.style.setProperty("--shell-link-color", getComputedStyle(link).color);
        link.remove();
    }

    function init() {
        hiddenFrame = document.getElementById("shell-hidden");
        exposeLinkColor();
        document.querySelectorAll("[data-shell-region]").forEach(function (element) {
            var name = element.dataset.shellRegion;
            var region = new Region(name, element, {history: name === "main"});
            regions.set(name, region);
            // stands in for the frame of that name (window.main, window.left, ...)
            Object.defineProperty(window, name, {value: frameProxy(region), configurable: true, writable: true});
        });

        // form.submit() does not fire a submit event, so the pages calling it need the same treatment
        var nativeSubmit = HTMLFormElement.prototype.submit;
        HTMLFormElement.prototype.submit = function () {
            if (!submitForm(this, null)) {
                nativeSubmit.call(this);
            }
        };
        document.addEventListener("click", onClick);
        document.addEventListener("submit", onSubmit);
        window.addEventListener("popstate", onPopState);

        regions.forEach(function (region) {
            var src = region.element.dataset.shellSrc;
            if (src) {
                region.load(src, {history: "replace"});
            }
        });
    }

    window.Shell = {
        regions: regions,
        isPage: isPage,
        navigate: function (url) {
            return regions.get("main").navigate(url);
        }
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
