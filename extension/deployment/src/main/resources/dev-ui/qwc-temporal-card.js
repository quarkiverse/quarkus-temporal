import { LitElement, html, css} from 'lit';
import { pages } from 'build-time-data';
import 'qwc/qwc-extension-link.js';

const NAME = "Temporal";
export class QwcTemporalCard extends LitElement {

    static styles = css`
      .identity {
        display: flex;
        justify-content: flex-start;
      }

      .description {
        padding-bottom: 10px;
      }

      .logo {
        padding-bottom: 10px;
        margin-right: 5px;
      }

      .card-content {
        color: var(--lumo-contrast-90pct);
        display: flex;
        flex-direction: column;
        justify-content: flex-start;
        padding: 2px 2px;
        height: 100%;
      }

      .card-content slot {
        display: flex;
        flex-flow: column wrap;
        padding-top: 5px;
      }
    `;

    static properties = {
        description: {type: String}
    };

    constructor() {
        super();
    }

    connectedCallback() {
        super.connectedCallback();
    }

    render() {
        return html`<div class="card-content" slot="content">
            <div class="identity">
                <div class="logo">
                    <img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxOTIiIGhlaWdodD0iMTkyIiBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMTkyIDE5MiI+PHJlY3Qgd2lkdGg9IjE5MiIgaGVpZ2h0PSIxOTIiIGZpbGw9InVybCgjcGFpbnQwX2xpbmVhcl8xNDUyXzUzMTcpIiByeD0iMjQiLz48cGF0aCBmaWxsPSIjRjJGMkYyIiBkPSJNMTIzLjM0IDY4LjY1OTZDMTE5LjY1NSA0MS4wNDg0IDExMC4zMjcgMTggOTYgMThDODEuNjczMSAxOCA3Mi4zNDU0IDQxLjA0ODQgNjguNjU5NiA2OC42NTk2QzQxLjA0ODQgNzIuMzQ1NCAxOCA4MS42NzMxIDE4IDk2QzE4IDExMC4zMjcgNDEuMDUyNSAxMTkuNjU1IDY4LjY1OTYgMTIzLjM0QzcyLjM0NTQgMTUwLjk0OCA4MS42NzMxIDE3NCA5NiAxNzRDMTEwLjMyNyAxNzQgMTE5LjY1NSAxNTAuOTQ4IDEyMy4zNCAxMjMuMzRDMTUwLjk1MiAxMTkuNjU1IDE3NCAxMTAuMzI3IDE3NCA5NkMxNzQgODEuNjczMSAxNTAuOTQ4IDcyLjM0NTQgMTIzLjM0IDY4LjY1OTZaTTY3Ljc1ODMgMTE1LjI5OEM0MS4zMTUxIDExMS40NzkgMjUuODkzIDEwMi43MzcgMjUuODkzIDk2QzI1Ljg5MyA4OS4yNjI5IDQxLjMxNTEgODAuNTIxMiA2Ny43NTgzIDc2LjcwMjFDNjcuMTc2NCA4My4wNjc0IDY2Ljg3MzMgODkuNTY2IDY2Ljg3MzMgOTZDNjYuODczMyAxMDIuNDM0IDY3LjE3NjQgMTA4LjkzNyA2Ny43NTgzIDExNS4yOThaTTk2IDI1Ljg5M0MxMDIuNzM3IDI1Ljg5MyAxMTEuNDc5IDQxLjMxNTEgMTE1LjI5OCA2Ny43NTgzQzEwOC45MzcgNjcuMTc2NCAxMDIuNDM0IDY2Ljg3MzMgOTYgNjYuODczM0M4OS41NjYgNjYuODczMyA4My4wNjMzIDY3LjE3NjQgNzYuNzAyMSA2Ny43NTgzQzgwLjUyMTIgNDEuMzE1MSA4OS4yNjI5IDI1Ljg5MyA5NiAyNS44OTNaTTEyNC4yNDIgMTE1LjI5OEMxMjIuOTQgMTE1LjQ4OCAxMTcuNjAyIDExNi4xMTQgMTE2LjI1MiAxMTYuMjQ4QzExNi4xMTggMTE3LjYwMiAxMTUuNDg4IDEyMi45MzYgMTE1LjMwMiAxMjQuMjM4QzExMS40ODMgMTUwLjY4MSAxMDIuNzQxIDE2Ni4xMDMgOTYuMDA0MSAxNjYuMTAzQzg5LjI2NyAxNjYuMTAzIDgwLjUyNTMgMTUwLjY4MSA3Ni43MDYxIDEyNC4yMzhDNzYuNTIwMiAxMjIuOTM2IDc1Ljg4OTggMTE3LjU5OCA3NS43NTY0IDExNi4yNDhDNzUuMTQyMSAxMDkuOTc5IDc0Ljc3MDMgMTAzLjI0NiA3NC43NzAzIDk2Qzc0Ljc3MDMgODguNzUzNyA3NS4xNDIxIDgyLjAyMDYgNzUuNzU2NCA3NS43NDgzQzgyLjAyNDcgNzUuMTM0IDg4Ljc1NzcgNzQuNzYyMiA5Ni4wMDQxIDc0Ljc2MjJDMTAzLjI1IDc0Ljc2MjIgMTA5Ljk4MyA3NS4xMzQgMTE2LjI1MiA3NS43NDgzQzExNy42MDYgNzUuODgxNyAxMjIuOTQgNzYuNTEyMSAxMjQuMjQyIDc2LjY5OEMxNTAuNjg1IDgwLjUxNzIgMTY2LjExMSA4OS4yNjI5IDE2Ni4xMTEgOTUuOTk2QzE2Ni4xMTEgMTAyLjcyOSAxNTAuNjg1IDExMS40NzkgMTI0LjI0MiAxMTUuMjk4WiIvPjxkZWZzPjxsaW5lYXJHcmFkaWVudCBpZD0icGFpbnQwX2xpbmVhcl8xNDUyXzUzMTciIHgxPSIxODMiIHgyPSIwIiB5MT0iMTkyIiB5Mj0iMCIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiPjxzdG9wIHN0b3AtY29sb3I9IiM0NDRDRTciLz48c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiNCNjY0RkYiLz48L2xpbmVhckdyYWRpZW50PjwvZGVmcz48L3N2Zz4="
                                       alt="${NAME}" 
                                       title="${NAME}"
                                       width="32" 
                                       height="32">
                </div>
                <div class="description">${this.description}</div>
            </div>
            ${this._renderCardLinks()}
        </div>
        `;
    }

    _renderCardLinks(){
        return html`${pages.map(page => html`
                            <qwc-extension-link slot="link"
                                extensionName="${NAME}"
                                iconName="${page.icon}"
                                displayName="${page.title}"
                                staticLabel="${page.staticLabel}"
                                dynamicLabel="${page.dynamicLabel}"
                                streamingLabel="${page.streamingLabel}"
                                path="${page.id}"
                                ?embed=${page.embed}
                                externalUrl="${page.metadata.externalUrl}"
                                webcomponent="${page.componentLink}" >
                            </qwc-extension-link>
                        `)}`;
    }

}
customElements.define('qwc-temporal-card', QwcTemporalCard);