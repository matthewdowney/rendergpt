(ns com.mjdowney.rendergpt
  (:require [crate.core :as crate]))

(defn inject-stylesheet [href]
  (let [link (js/document.createElement "link")]
    (set! (.-rel link) "stylesheet")
    (set! (.-type link) "text/css")
    (set! (.-href link) href)
    (.appendChild (.-head js/document) link)))

(defn build-render-button []
  (crate/html [:button.flex.ml-auto.gap-2 "Render"]))

(defn on-mutation [mutation-records _observer]
  (js/console.log "Mutation observed" mutation-records)
  (doseq [ele (js/document.getElementsByClassName "bg-black mb-4 rounded-md")]
    (when-let [code-block-type (some-> (.getElementsByTagName ele "span") first)]
      (when (= (.-innerText code-block-type) "html")
        (js/console.log "Found html" code-block-type)
        (set! (.-innerText code-block-type) "html+")

        (let [render-button (build-render-button)]
          (if-let [existing-button (first (.getElementsByTagName ele "button"))]
            (.insertBefore (.-parentNode existing-button) render-button existing-button)
            (.appendChild (.-parentElement code-block-type) render-button)))))))

(defn register-on-mutation [f]
  (let [observer (js/MutationObserver. f)]
    (.observe observer
      (.-body js/document)
      #js {:childList true :subtree true})))

(defn init []
  (inject-stylesheet (js/chrome.runtime.getURL "rendergpt.css"))
  (register-on-mutation on-mutation)
  (js/console.log "injected rendergpt"))
