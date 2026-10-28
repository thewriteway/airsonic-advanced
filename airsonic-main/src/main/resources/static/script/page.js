/*
 * Page runtime: lifecycle, declarative event handlers and dialogs for the Airsonic pages.
 *
 * Lifecycle. A page registers its code with Page.define(name, setup) and marks its root element
 * (usually <body>) with data-page="name". When the root is mounted, setup(page) runs with a page
 * object that owns everything the page starts: timers, listeners on elements outside the page,
 * websocket subscriptions and cleanup callbacks. Unmounting the root (the shell does this before it
 * shows another page in the same region) releases all of it, so nothing leaks between pages and a
 * page can be shown any number of times. Page scripts keep their state inside setup, never in
 * global variables.
 *
 * Handlers. Elements declare handlers with data-on-<event>="actionName" (click, change, input,
 * submit, ...) instead of inline on<event> attributes, which the Content-Security-Policy forbids.
 * The action is looked up in the nearest mounted page around the element (page.actions({...})),
 * then in the global actions (Page.actions({...})). It is called with the element as first argument
 * and the event as second, and returning false prevents the default action and stops propagation.
 * Clicks on links to "#" never navigate.
 *
 * Standalone documents mount their [data-page] elements when the DOM is ready; the application
 * shell mounts and unmounts its regions itself.
 */
(function () {
    "use strict";

    var EVENTS = ["click", "dblclick", "change", "input", "submit", "keydown", "keyup", "focusout"];

    var definitions = new Map();
    var instances = new WeakMap();
    // actions by name; Maps, so that names taken from the markup never reach object properties
    var globalActions = new Map();

    /* Actions every page can use */
    globalActions.set("historyBack", function () {
        window.history.back();
        return false;
    });
    globalActions.set("closeWindow", function () {
        window.close();
        return false;
    });
    globalActions.set("reloadApplication", function () {
        window.top.location.reload();
        return false;
    });
    /**
     * Asks for confirmation (data-confirm) before a submit button submits its form. Put it on the
     * button: the form is submitted again with the same submitter once confirmed.
     */
    globalActions.set("confirmSubmit", function (button) {
        confirmDialog(button.getAttribute("data-confirm")).then(function (ok) {
            if (ok) {
                button.form.requestSubmit(button);
            }
        });
        return false;
    });
    /** Closes the <dialog> around the element. */
    globalActions.set("closeDialog", function (element) {
        element.closest("dialog").close();
        return false;
    });
    /** Navigates the main page (or this window outside the shell) to the value of a select. */
    globalActions.set("navigateToValue", function (select) {
        navigate(select.value);
    });
    /** Navigates the main page (or this window outside the shell) to data-href or the link's href. */
    globalActions.set("navigate", function (element) {
        navigate(element.getAttribute("data-href") || element.getAttribute("href"));
        return false;
    });
    var sequence = 0;

    function PageInstance(name, root) {
        this.name = name;
        this.root = root;
        this.id = "page:" + name + ":" + (++sequence);
        this.handlers = new Map();
        this.cleanups = [];
        this.timers = new Set();
        this.destroyed = false;
    }

    /** Registers actions for data-on-* handlers inside this page. */
    PageInstance.prototype.actions = function (actions) {
        Object.entries(actions).forEach(function (entry) {
            this.handlers.set(entry[0], entry[1]);
        }, this);
        return this;
    };

    /** Runs one of the page's actions by name, as a data-on-* attribute would. */
    PageInstance.prototype.run = function (name, element, event) {
        var handler = this.handlers.get(name);
        return typeof handler === "function" ? handler.call(this, element, event) : undefined;
    };

    /** Runs a callback when the page is unmounted. */
    PageInstance.prototype.onDestroy = function (callback) {
        this.cleanups.push(callback);
    };

    PageInstance.prototype.setTimeout = function (callback, delay) {
        var timers = this.timers;
        var id = window.setTimeout(function () {
            timers.delete(id);
            callback();
        }, delay);
        timers.add(id);
        return id;
    };

    PageInstance.prototype.setInterval = function (callback, delay) {
        var id = window.setInterval(callback, delay);
        this.timers.add(id);
        return id;
    };

    PageInstance.prototype.clearTimer = function (id) {
        // timeout and interval ids share one pool, so either function clears both kinds
        window.clearTimeout(id);
        this.timers.delete(id);
    };

    /** Adds an event listener to an element outside the page (window, document, ...) for the page's lifetime. */
    PageInstance.prototype.listen = function (target, type, listener, options) {
        target.addEventListener(type, listener, options);
        this.cleanups.push(function () {
            target.removeEventListener(type, listener, options);
        });
    };

    /** Subscribes to websocket topics ({topic: callback(message)}) for the page's lifetime. */
    PageInstance.prototype.subscribe = function (subscriptions, callback) {
        var owner = this.id;
        window.StompClient.subscribe(owner, subscriptions, callback);
        if (this.cleanups.indexOf(this.unsubscribeAll) === -1) {
            this.unsubscribeAll = function () {
                window.StompClient.unsubscribeOwner(owner);
            };
            this.cleanups.push(this.unsubscribeAll);
        }
    };

    PageInstance.prototype.find = function (selector) {
        return this.root.querySelector(selector);
    };

    PageInstance.prototype.findAll = function (selector) {
        return Array.prototype.slice.call(this.root.querySelectorAll(selector));
    };

    PageInstance.prototype.destroy = function () {
        this.destroyed = true;
        this.timers.forEach(function (id) {
            window.clearTimeout(id);
        });
        this.timers.clear();
        while (this.cleanups.length > 0) {
            var cleanup = this.cleanups.pop();
            try {
                cleanup.call(this);
            } catch (e) {
                console.error("Cleanup of page " + this.name + " failed", e);
            }
        }
    };

    function mount(root) {
        unmount(root);
        var name = root.getAttribute("data-page");
        if (!name) {
            return null;
        }
        var setup = definitions.get(name);
        if (!setup) {
            console.warn("No page named " + name + " is defined");
            return null;
        }
        var page = new PageInstance(name, root);
        instances.set(root, page);
        if (window.Icons) {
            window.Icons.replace(root);
        }
        try {
            var cleanup = setup.call(page, page);
            if (typeof cleanup === "function") {
                page.onDestroy(cleanup);
            }
        } catch (e) {
            console.error("Could not set up page " + name, e);
        }
        return page;
    }

    function unmount(root) {
        var page = instances.get(root);
        if (page) {
            instances.delete(root);
            page.destroy();
        }
    }

    function findAction(element, name) {
        for (var node = element; node; node = node.parentElement) {
            var page = instances.get(node);
            var handler = page && page.handlers.get(name);
            if (typeof handler === "function") {
                return {handler: handler, page: page};
            }
        }
        var global = globalActions.get(name);
        return typeof global === "function" ? {handler: global, page: null} : null;
    }

    function dispatch(event) {
        var attribute = "data-on-" + event.type;
        var target = event.target;
        var element = target && target.closest ? target.closest("[" + attribute + "]") : null;
        if (!element) {
            return;
        }
        var name = element.getAttribute(attribute);
        var action = findAction(element, name);
        if (!action) {
            console.warn("No action named " + name + " for " + event.type + " events");
            return;
        }
        if (event.type === "click" && element.tagName === "A" && element.getAttribute("href") === "#") {
            event.preventDefault();
        }
        if (action.handler.call(action.page, element, event) === false) {
            event.preventDefault();
            event.stopPropagation();
        }
    }

    EVENTS.forEach(function (type) {
        document.addEventListener(type, dispatch);
    });

    /* Dialogs (native <dialog>) */

    var labels = {ok: "OK", cancel: "Cancel"};

    /**
     * Shows a <dialog> as a modal. Buttons of a form with method="dialog" close it; the promise
     * resolves with the value of the button used, or "" when the dialog was dismissed (Esc).
     */
    function openDialog(dialog) {
        return new Promise(function (resolve) {
            var done = false;

            function finish(value) {
                if (!done) {
                    done = true;
                    dialog.removeEventListener("submit", onSubmit);
                    dialog.removeEventListener("close", onClose);
                    resolve(value);
                }
            }

            // the submission tells the button used right away; close events wait for rendering
            function onSubmit(event) {
                var method = (event.submitter && event.submitter.getAttribute("formmethod")) || event.target.getAttribute("method");
                if (event.target.closest("dialog") === dialog && String(method).toLowerCase() === "dialog") {
                    finish(event.submitter ? event.submitter.value : "");
                }
            }

            function onClose() {
                finish(dialog.returnValue);
            }

            dialog.addEventListener("submit", onSubmit);
            dialog.addEventListener("close", onClose);
            dialog.returnValue = "";
            dialog.showModal();
        });
    }

    function messageDialog(message, buttons) {
        var dialog = document.createElement("dialog");
        dialog.className = "page-dialog";
        var form = document.createElement("form");
        form.method = "dialog";
        var text = document.createElement("p");
        text.textContent = message;
        var menu = document.createElement("div");
        menu.className = "page-dialog-buttons";
        buttons.forEach(function (button) {
            var element = document.createElement("button");
            element.value = button.value;
            element.textContent = button.label;
            menu.appendChild(element);
        });
        form.append(text, menu);
        dialog.appendChild(form);
        document.body.appendChild(dialog);
        return openDialog(dialog).then(function (value) {
            dialog.remove();
            return value;
        });
    }

    function confirmDialog(message) {
        return messageDialog(message, [{value: "ok", label: labels.ok}, {value: "cancel", label: labels.cancel}])
            .then(function (value) {
                return value === "ok";
            });
    }

    function alertDialog(message) {
        return messageDialog(message, [{value: "ok", label: labels.ok}]).then(function () {
            return undefined;
        });
    }

    /* Navigation */

    function navigate(url) {
        if (window.Shell) {
            return window.Shell.navigate(url);
        }
        window.location.href = url;
        return Promise.resolve();
    }

    /** Loads a URL into a region of the shell (left, right, main). */
    function loadRegion(name, url) {
        var region = window.Shell && window.Shell.regions.get(name);
        return region ? region.navigate(url) : Promise.resolve();
    }

    function reloadRegion(name) {
        var region = window.Shell && window.Shell.regions.get(name);
        if (region) {
            return region.reload();
        }
        if (name === "main") {
            window.location.reload();
        }
        return Promise.resolve();
    }

    window.Page = {
        /** Registers the setup function of a page; setup(page) may return a cleanup function. */
        define: function (name, setup) {
            definitions.set(name, setup);
        },
        /** Registers actions available everywhere (used by the persistent parts of the shell). */
        actions: function (actions) {
            Object.entries(actions).forEach(function (entry) {
                globalActions.set(entry[0], entry[1]);
            });
        },
        mount: mount,
        unmount: unmount,
        /** The mounted page around an element, if any. */
        of: function (element) {
            for (var node = element; node; node = node.parentElement) {
                if (instances.has(node)) {
                    return instances.get(node);
                }
            }
            return null;
        },
        navigate: navigate,
        reloadRegion: reloadRegion,
        loadRegion: loadRegion,
        openDialog: openDialog,
        confirm: confirmDialog,
        alert: alertDialog,
        setLabels: function (values) {
            Object.assign(labels, values);
        }
    };

    function mountAll() {
        document.querySelectorAll("[data-page]").forEach(function (root) {
            if (!instances.has(root)) {
                mount(root);
            }
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", mountAll);
    } else {
        // loaded at the end of a document: mount once the current script is done
        window.setTimeout(mountAll);
    }
})();
