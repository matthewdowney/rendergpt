(ns com.mjdowney.rendergpt
  (:require [goog.dom :as gdom]))

(defn inject-stylesheet [href]
  (let [link (gdom/createElement "link")]
    (set! (.-rel link) "stylesheet")
    (set! (.-type link) "text/css")
    (set! (.-href link) href)
    (.appendChild (.-head js/document) link)))

(defn on-mutation [mutation-records _observer]
  (js/console.log "Mutation observed" mutation-records)
  (doseq [ele (js/document.getElementsByClassName "bg-black mb-4 rounded-md")]
    (when-let [code-block-type (some-> (.getElementsByTagName ele "span") first)]
      (when (= (.-innerText code-block-type) "html")
        (js/console.log "Found html" code-block-type)
        (set! (.-innerText code-block-type) "html+")))))

(defn register-on-mutation [f]
  (let [observer (js/MutationObserver. f)]
    (.observe observer
      (.-body js/document)
      #js {:childList true :subtree true})))

(defn init []
  (inject-stylesheet (js/chrome.runtime.getURL "rendergpt.css"))
  (register-on-mutation on-mutation)
  (js/console.log "injected rendergpt"))
