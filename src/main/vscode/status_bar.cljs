(ns vscode.status-bar
  (:require ["vscode" :refer [window MarkdownString]]
            [utils.interop :refer [keyword-to-path]]))

(set! *warn-on-infer* false)

(defn create [alignment priority]
  (.createStatusBarItem window
                        (case alignment
                          :right 2
                          1)
                        priority))

(defn- ->markdown [markdown-source]
  ;; Accept either an existing MarkdownString or a plain string. Plain strings
  ;; are wrapped with:
  ;;  - supportThemeIcons=true so $(icon) sequences render
  ;;  - isTrusted=true so command: links are clickable
  ;;  - supportHtml=true so <h2>, <span style="color:..."> etc. render
  (if (string? markdown-source)
    (let [md (MarkdownString. markdown-source true)]
      (set! (.-isTrusted md) true)
      (set! (.-supportHtml md) true)
      md)
    markdown-source))

(defn show [{:keys [text command tooltip]} status]
  (set! (.-text status) (clj->js text))
  (when command
    (set! (.-command status) (keyword-to-path command)))
  (when (some? tooltip)
    (set! (.-tooltip status) (->markdown tooltip)))
  (.show status))

(defn hide [status]
  (.hide status))
