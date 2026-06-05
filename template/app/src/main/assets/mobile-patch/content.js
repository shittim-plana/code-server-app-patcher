(function () {
  "use strict";

  var PATCH_RETRY_MS = 2000;
  var MAX_RETRIES = 15;
  var retryCount = 0;

  function waitForMonaco() {
    if (typeof window.monaco !== "undefined" || document.querySelector(".monaco-editor")) {
      applyPatches();
      return;
    }
    retryCount++;
    if (retryCount < MAX_RETRIES) {
      setTimeout(waitForMonaco, PATCH_RETRY_MS);
    }
  }

  function applyPatches() {
    patchSafeArea();
    patchViewportHeight();
    patchViewportResize();
    patchClipboardPaste();
    patchTouchSelection();
    patchEnterKey();
  }

  function patchSafeArea() {
    var style = document.createElement("style");
    style.textContent =
      "body { " +
      "  padding-bottom: env(safe-area-inset-bottom, 0px) !important; " +
      "  box-sizing: border-box !important; " +
      "}" +
      ".monaco-workbench .part.statusbar { " +
      "  padding-bottom: env(safe-area-inset-bottom, 0px); " +
      "}";
    document.head.appendChild(style);
  }

  function patchViewportHeight() {
    function setVh() {
      var vh = window.innerHeight * 0.01;
      document.documentElement.style.setProperty("--vh", vh + "px");
      document.body.style.height = window.innerHeight + "px";
    }
    setVh();
    window.addEventListener("resize", setVh);
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", setVh);
    }
  }

  function patchViewportResize() {
    if (!window.visualViewport) return;

    var layoutTimeout = null;

    window.visualViewport.addEventListener("resize", function () {
      if (layoutTimeout) clearTimeout(layoutTimeout);
      layoutTimeout = setTimeout(function () {
        triggerMonacoLayout();
      }, 150);
    });

    window.visualViewport.addEventListener("scroll", function () {
      if (layoutTimeout) clearTimeout(layoutTimeout);
      layoutTimeout = setTimeout(function () {
        triggerMonacoLayout();
      }, 150);
    });
  }

  function triggerMonacoLayout() {
    var editors = document.querySelectorAll(".monaco-editor");
    editors.forEach(function (el) {
      window.dispatchEvent(new Event("resize"));
    });

    if (window.monaco && window.monaco.editor) {
      var instances = window.monaco.editor.getEditors
        ? window.monaco.editor.getEditors()
        : [];
      instances.forEach(function (editor) {
        try {
          editor.layout();
        } catch (e) {}
      });
    }
  }

  function patchClipboardPaste() {
    document.addEventListener(
      "paste",
      function (e) {
        var clipboardData = e.clipboardData;
        if (!clipboardData) return;

        var text = clipboardData.getData("text/plain");
        if (!text) return;

        var normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");

        if (normalized !== text) {
          e.preventDefault();

          if (window.monaco && window.monaco.editor) {
            var focusedEditor = null;
            var instances = window.monaco.editor.getEditors
              ? window.monaco.editor.getEditors()
              : [];
            for (var i = 0; i < instances.length; i++) {
              if (instances[i].hasTextFocus && instances[i].hasTextFocus()) {
                focusedEditor = instances[i];
                break;
              }
            }

            if (focusedEditor) {
              focusedEditor.trigger("mobile-patch", "type", { text: normalized });
              return;
            }
          }

          document.execCommand("insertText", false, normalized);
        }
      },
      true
    );
  }

  function patchTouchSelection() {
    var longPressTimer = null;
    var LONG_PRESS_MS = 500;
    var startX = 0;
    var startY = 0;
    var MOVE_THRESHOLD = 10;

    document.addEventListener(
      "touchstart",
      function (e) {
        if (!isInMonacoEditor(e.target)) return;

        var touch = e.touches[0];
        startX = touch.clientX;
        startY = touch.clientY;

        longPressTimer = setTimeout(function () {
          if (window.monaco && window.monaco.editor) {
            var instances = window.monaco.editor.getEditors
              ? window.monaco.editor.getEditors()
              : [];
            for (var i = 0; i < instances.length; i++) {
              var editor = instances[i];
              try {
                var target = editor.getTargetAtClientPoint(startX, startY);
                if (target && target.position) {
                  editor.setPosition(target.position);
                  editor.getAction("editor.action.selectHighlights")
                    ? editor.trigger("mobile-patch", "editor.action.smartSelect.expand", {})
                    : null;
                }
              } catch (ex) {}
            }
          }
        }, LONG_PRESS_MS);
      },
      { passive: true }
    );

    document.addEventListener(
      "touchmove",
      function (e) {
        if (!longPressTimer) return;
        var touch = e.touches[0];
        var dx = Math.abs(touch.clientX - startX);
        var dy = Math.abs(touch.clientY - startY);
        if (dx > MOVE_THRESHOLD || dy > MOVE_THRESHOLD) {
          clearTimeout(longPressTimer);
          longPressTimer = null;
        }
      },
      { passive: true }
    );

    document.addEventListener("touchend", function () {
      if (longPressTimer) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
      }
    });
  }

  function patchEnterKey() {
    var enterHandledByEngine = false;

    document.addEventListener("beforeinput", function (e) {
      if (e.inputType === "insertLineBreak" || e.inputType === "insertParagraph") {
        enterHandledByEngine = true;
      }
    }, true);

    document.addEventListener("keydown", function (e) {
      if (e.key !== "Enter") return;

      enterHandledByEngine = false;
      setTimeout(function () {
        if (enterHandledByEngine) return;

        if (window.monaco && window.monaco.editor) {
          var editors = window.monaco.editor.getEditors
            ? window.monaco.editor.getEditors()
            : [];
          for (var i = 0; i < editors.length; i++) {
            if (editors[i].hasTextFocus && editors[i].hasTextFocus()) {
              editors[i].trigger("mobile-patch", "type", { text: "\n" });
              return;
            }
          }
        }

        var active = document.activeElement;
        if (active && (active.tagName === "TEXTAREA" ||
            (active.tagName === "INPUT" && active.type === "text") ||
            active.isContentEditable)) {
          document.execCommand("insertText", false, "\n");
        }
      }, 0);
    }, true);
  }

  function isInMonacoEditor(el) {
    while (el) {
      if (el.classList && el.classList.contains("monaco-editor")) return true;
      el = el.parentElement;
    }
    return false;
  }

  if (document.readyState === "complete" || document.readyState === "interactive") {
    waitForMonaco();
  } else {
    document.addEventListener("DOMContentLoaded", waitForMonaco);
  }
})();
