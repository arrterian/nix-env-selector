// Permissive ESM mock of the `vscode` runtime module so namespaces that
// import it can be loaded under shadow-cljs `:node-test`. Tests should not
// call vscode-dependent functions — exercise pure helpers via `#'private`
// refs only.
//
// shadow-cljs treats `module.exports` here as a default export, so named
// `export` statements are required for `(:require ["vscode" :refer [...]])`
// to resolve.

function makeProxy () {
  return new Proxy(function () { return makeProxy() }, {
    get (target, prop) {
      if (prop === 'then' || prop === Symbol.toPrimitive ||
          prop === Symbol.iterator || prop === Symbol.asyncIterator ||
          prop === 'constructor' || prop === 'prototype') {
        return undefined
      }
      return makeProxy()
    },
    apply () { return makeProxy() },
    construct () { return makeProxy() }
  })
}

export const window = makeProxy()
export const workspace = makeProxy()
export const commands = makeProxy()
export const Uri = makeProxy()
export const StatusBarAlignment = { Left: 1, Right: 2 }
export function EventEmitter () { return makeProxy() }

export class MarkdownString {
  constructor (value, supportThemeIcons) {
    this.value = value
    this.supportThemeIcons = !!supportThemeIcons
    this.isTrusted = false
    this.supportHtml = false
  }
}
