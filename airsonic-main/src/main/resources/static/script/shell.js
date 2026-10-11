/*
 * Application shell: hosts the Airsonic pages in regions of a single document.
 *
 * The pages were written for a frameset (upper, left, main and right iframes) and are still
 * complete HTML documents. A region loads such a document with fetch, moves its body into the
 * region, runs its scripts in document order (libraries the shell already has are not loaded
 * twice) and finally runs its body onload handler, which is what the pages expect from a frame.
 *
 * Links and forms that used to navigate a frame now load the corresponding region, and the main
 * region keeps the browser history and address bar in sync. The frame references used by the
 * pages (top.main.location, parent.frames.left.location, top.main.$, ...) are emulated by
 * window.main, window.left, window.right and window.upper, so the pages keep working unchanged.
 *
 * Expects window.ShellConfig = {base: context path with trailing slash, pages: [page names]}, and
 * elements with data-shell-region (region name) and data-shell-src (initial URL, if any).
 */
(function () {
    "use strict";

    var config = window.ShellConfig;
    var pages = new Set(config.pages);
    var regions = {};
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

    /** Runs inline handler code (e.g. a body onload attribute) in the global scope. */
    function runInline(code) {
        var script = document.createElement("script");
        script.textContent = code;
        document.head.appendChild(script);
        script.remove();
    }

    function Region(name, element, options) {
        this.name = name;
        this.element = element;
        this.content = element.querySelector(".shell-content");
        this.history = options && options.history;
        this.url = null;
        this.styles = [];
        this.sequence = 0;
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
        var region = this;
        var sequence = ++region.sequence;
        var hash = resolve(url).hash;
        region.element.setAttribute("aria-busy", "true");

        return fetch(url, {
            method: options.method || "GET",
            body: options.body,
            credentials: "same-origin",
            headers: {"X-Airsonic-Shell": region.name}
        }).then(function (response) {
            if (sequence !== region.sequence) {
                return null;
            }
            if (isLoginPage(response.url)) {
                // session expired: leave the shell
                window.location.assign(response.url);
                return null;
            }
            if ((response.headers.get("Content-Type") || "").indexOf("text/html") === -1) {
                if (!options.method || options.method === "GET") {
                    openInHiddenFrame(url);
                }
                return null;
            }
            return response.text().then(function (html) {
                if (sequence === region.sequence) {
                    var finalUrl = resolve(response.url);
                    if (!finalUrl.hash && hash && response.url.split("#")[0] === url.split("#")[0]) {
                        finalUrl.hash = hash;
                    }
                    return region.render(html, finalUrl.href, options);
                }
            });
        }).catch(function (error) {
            console.error("Could not load " + url + " into the " + region.name + " region", error);
        }).finally(function () {
            if (sequence === region.sequence) {
                region.element.removeAttribute("aria-busy");
            }
        });
    };

    Region.prototype.render = function (html, url, options) {
        var region = this;
        var doc = new DOMParser().parseFromString(html, "text/html");
        region.url = url;

        if (region.history) {
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
        region.styles.forEach(function (style) {
            style.remove();
        });
        region.styles = Array.prototype.map.call(doc.head.querySelectorAll("style"), function (parsed) {
            var style = document.createElement("style");
            style.textContent = parsed.textContent;
            style.dataset.shellRegion = region.name;
            document.head.appendChild(style);
            return style;
        });

        // Release jQuery data and handlers of the previous page, then show the new body.
        if (window.jQuery) {
            window.jQuery(region.content).empty();
        }
        region.content.className = "shell-content " + doc.body.className;
        region.content.setAttribute("style", doc.body.getAttribute("style") || "");
        // copy first: adopting a node removes it from the live childNodes list
        region.content.replaceChildren.apply(region.content, Array.from(doc.body.childNodes).map(function (node) {
            return document.adoptNode(node);
        }));

        var anchor = new URL(url).hash.substring(1);
        var anchorTarget = anchor && (document.getElementById(anchor) || document.getElementsByName(anchor)[0]);
        if (anchorTarget) {
            anchorTarget.scrollIntoView();
        } else {
            region.element.scrollTop = 0;
        }

        var scripts = Array.prototype.slice.call(doc.head.querySelectorAll("script"))
            .concat(Array.prototype.slice.call(region.content.querySelectorAll("script")));
        var onload = doc.body.getAttribute("onload");
        return scripts.reduce(function (previous, script) {
            return previous.then(function () {
                return runScript(script, url);
            });
        }, Promise.resolve()).then(function () {
            // jQuery ready handlers run asynchronously; the load event used to come after them.
            setTimeout(function () {
                if (onload) {
                    runInline(onload);
                }
                region.element.dispatchEvent(new CustomEvent("shell:load", {detail: {url: url}}));
            });
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
            return regions[target] || null;
        }
        var container = element.closest("[data-shell-region]");
        if (container) {
            return regions[container.dataset.shellRegion] || null;
        }
        return isPage(url) ? regions.main : null;
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
        var region = targetRegion(link, target, link.href);
        if (!region) {
            return;
        }
        if (!isPage(link.href)) {
            // a link to a download in a region: only intercept when it names a frame that no longer exists
            if (target && regions[target]) {
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
        var url = resolve(action);
        if (url.origin !== location.origin || isLoginPage(url) || /\/logout$/.test(url.pathname)
            || (method === "get" && !isPage(url))) {
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
        if (event.state && event.state.shell && event.state.shell !== regions.main.url) {
            regions.main.load(event.state.shell, {history: "none"});
        }
    }

    function init() {
        hiddenFrame = document.getElementById("shell-hidden");
        document.querySelectorAll("[data-shell-region]").forEach(function (element) {
            var name = element.dataset.shellRegion;
            regions[name] = new Region(name, element, {history: name === "main"});
            window[name] = frameProxy(regions[name]);
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

        Object.keys(regions).forEach(function (name) {
            var src = regions[name].element.dataset.shellSrc;
            if (src) {
                regions[name].load(src, {history: "replace"});
            }
        });
    }

    window.Shell = {
        regions: regions,
        isPage: isPage,
        navigate: function (url) {
            return regions.main.navigate(url);
        }
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
