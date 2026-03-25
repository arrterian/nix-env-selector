(ns vscode.terminal
  (:require ["vscode" :as vscode]))

(defn on-did-open-terminal [handler]
  (.. vscode/window (onDidOpenTerminal handler)))

(defn send-text [terminal text]
  (.sendText terminal text))

(defn user-terminal? [terminal]
  ;; Extension-created terminals always set a name; user-opened ones have
  ;; an empty or undefined name (defaults to the shell executable).
  (empty? (.-name terminal)))
