(function () {
    try {
        // 隐藏悬浮层，避免遮挡
        let style = document.getElementById("lukos-hide");
        if (!style) {
            style = document.createElement("style");
            style.id = "lukos-hide";
            style.textContent = `
                .uls-menu, .uls-dialog, .vector-sticky-header,
                .mw-banner-ct-container, .centralNotice { display:none !important; }
              `;
            document.documentElement.appendChild(style);
        }
        window.scrollTo(0, 0);

        const pick = () =>
            document.querySelector("#mw-content-text .mw-parser-output") ||
            document.querySelector(".mw-content-ltr .mw-parser-output") ||
            document.querySelector(".mw-parser-output");

        const container = pick();
        if (!container) return 1000;

        const kids = Array.from(container.children);

        // A) 第一个标题及其 top
        let firstHeading = null;
        for (const el of kids) {
            if (/^H[1-6]$/.test(el.tagName)) {
                firstHeading = el;
                break;
            }
        }
        let headingTop = 0;
        if (firstHeading) {
            const hRect = firstHeading.getBoundingClientRect();
            headingTop = Math.max(0, Math.floor(hRect.top + window.scrollY));
        }

        // B) 标题前最后一个非空 <p> 的 bottom
        let lastP = null;
        for (const el of kids) {
            if (firstHeading && el === firstHeading) break;
            if (el.tagName === "P" && el.innerText.trim().length > 0) lastP = el;
        }
        let lastPBottom = 0;
        if (lastP) {
            const r = lastP.getBoundingClientRect();
            lastPBottom = Math.ceil(r.bottom + window.scrollY);
        }

        // C) infobox bottom（但不超过标题 top）
        const infobox = container.querySelector(
            "table.infobox, table.infobox_v2, .infobox, .infobox_v2"
        );
        let infoboxBottom = 0;
        if (infobox) {
            const ri = infobox.getBoundingClientRect();
            infoboxBottom = Math.ceil(ri.bottom + window.scrollY);
        }
        if (headingTop > 0)
            infoboxBottom = Math.min(infoboxBottom || 0, headingTop);

        // D) 合成：取较大者，加余量，上限限制
        let base = Math.max(lastPBottom, infoboxBottom);
        if (!base || base < 600) base = 900;
        return Math.max(1, Math.floor(Math.min(base + 120, 2600)));
    } catch (e) {
        return 1200;
    }
})();
