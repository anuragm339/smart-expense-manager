const chapters = [
    { id: "readme", title: "About this book", file: "README.md" },
    { id: "architecture", title: "Architecture", file: "architecture.md" },
    { id: "startup-auth", title: "Startup and authentication", file: "startup-auth.md" },
    { id: "permissions-navigation", title: "Permissions and navigation", file: "permissions-navigation.md" },
    { id: "sms-ingestion", title: "SMS ingestion", file: "sms-ingestion.md" },
    { id: "transaction-persistence", title: "Transaction persistence", file: "transaction-persistence.md" },
    { id: "dashboard", title: "Dashboard", file: "dashboard.md" },
    { id: "messages-transactions", title: "Messages and transaction details", file: "messages-transactions.md" },
    { id: "categories-merchants", title: "Categories and merchants", file: "categories-merchants.md" },
    { id: "budgets", title: "Budgets", file: "budgets.md" },
    { id: "ai-insights", title: "AI insights", file: "ai-insights.md" },
    { id: "notifications", title: "Notifications", file: "notifications.md" },
    { id: "export-profile-settings", title: "Export, profile, and settings", file: "export-profile-settings.md" },
    { id: "dependency-injection-events", title: "Dependency injection and events", file: "dependency-injection-events.md" },
    { id: "build-testing-risks", title: "Build, testing, and risks", file: "build-testing-risks.md" },
];

const content = document.querySelector("#chapterContent");
const chapterNav = document.querySelector("#chapterNav");
const chapterSearch = document.querySelector("#chapterSearch");
const chapterPosition = document.querySelector("#chapterPosition");
const progressBar = document.querySelector("#progressBar");
const previousChapter = document.querySelector("#previousChapter");
const nextChapter = document.querySelector("#nextChapter");
const sourceChapter = document.querySelector("#sourceChapter");

let currentIndex = 0;

function escapeHtml(value) {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

function renderInline(value) {
    return escapeHtml(value)
        .replace(/`([^`]+)`/g, "<code>$1</code>")
        .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
        .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
}

function headingId(value) {
    return value.toLowerCase().replace(/[^a-z0-9\s-]/g, "").trim().replace(/\s+/g, "-");
}

function renderMarkdown(markdown) {
    const lines = markdown.replace(/\r/g, "").split("\n");
    const output = [];
    let paragraph = [];
    let inCode = false;
    let codeLanguage = "";
    let codeLines = [];
    let listType = null;

    const flushParagraph = () => {
        if (paragraph.length) {
            output.push(`<p>${renderInline(paragraph.join(" "))}</p>`);
            paragraph = [];
        }
    };

    const closeList = () => {
        if (listType) {
            output.push(`</${listType}>`);
            listType = null;
        }
    };

    for (const line of lines) {
        const fence = line.match(/^```(.*)$/);
        if (fence) {
            flushParagraph();
            closeList();
            if (inCode) {
                output.push(`<pre><code class="language-${escapeHtml(codeLanguage)}">${escapeHtml(codeLines.join("\n"))}</code></pre>`);
                codeLines = [];
                codeLanguage = "";
            } else {
                codeLanguage = fence[1].trim();
            }
            inCode = !inCode;
            continue;
        }

        if (inCode) {
            codeLines.push(line);
            continue;
        }

        const heading = line.match(/^(#{1,3})\s+(.+)$/);
        const unordered = line.match(/^\s*-\s+(.+)$/);
        const ordered = line.match(/^\s*\d+\.\s+(.+)$/);

        if (heading) {
            flushParagraph();
            closeList();
            const level = heading[1].length;
            output.push(`<h${level} id="${headingId(heading[2])}">${renderInline(heading[2])}</h${level}>`);
        } else if (unordered || ordered) {
            flushParagraph();
            const requestedType = unordered ? "ul" : "ol";
            if (listType !== requestedType) {
                closeList();
                listType = requestedType;
                output.push(`<${listType}>`);
            }
            output.push(`<li>${renderInline((unordered || ordered)[1])}</li>`);
        } else if (/^>\s?/.test(line)) {
            flushParagraph();
            closeList();
            output.push(`<blockquote>${renderInline(line.replace(/^>\s?/, ""))}</blockquote>`);
        } else if (/^---+$/.test(line.trim())) {
            flushParagraph();
            closeList();
            output.push("<hr>");
        } else if (!line.trim()) {
            flushParagraph();
            closeList();
        } else {
            closeList();
            paragraph.push(line.trim());
        }
    }

    flushParagraph();
    closeList();
    if (inCode) {
        output.push(`<pre><code>${escapeHtml(codeLines.join("\n"))}</code></pre>`);
    }
    return output.join("\n");
}

function renderNavigation(filter = "") {
    const query = filter.trim().toLowerCase();
    chapterNav.innerHTML = chapters.map((chapter, index) => {
        const hidden = query && !chapter.title.toLowerCase().includes(query);
        return `
            <a class="chapter-link" href="#${chapter.id}" data-index="${index}"
               ${index === currentIndex ? 'aria-current="page"' : ""}
               ${hidden ? 'hidden' : ""}>
                <span class="chapter-number">${String(index + 1).padStart(2, "0")}</span>
                <span class="chapter-title">${chapter.title}</span>
            </a>`;
    }).join("");
}

function updatePageTurner() {
    const previous = chapters[currentIndex - 1];
    const next = chapters[currentIndex + 1];

    previousChapter.classList.toggle("is-disabled", !previous);
    previousChapter.href = previous ? `#${previous.id}` : "#";
    previousChapter.querySelector("strong").textContent = previous?.title || "";

    nextChapter.classList.toggle("is-disabled", !next);
    nextChapter.href = next ? `#${next.id}` : "#";
    nextChapter.querySelector("strong").textContent = next?.title || "";

    const chapter = chapters[currentIndex];
    sourceChapter.href = chapter.file;
    chapterPosition.textContent = `Chapter ${currentIndex + 1} of ${chapters.length}`;
    progressBar.style.width = `${((currentIndex + 1) / chapters.length) * 100}%`;
}

async function openChapter(index) {
    currentIndex = Math.max(0, Math.min(index, chapters.length - 1));
    const chapter = chapters[currentIndex];
    content.innerHTML = '<div class="loading-state">Turning the page...</div>';
    renderNavigation(chapterSearch.value);
    updatePageTurner();

    try {
        const response = await fetch(chapter.file);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        content.innerHTML = renderMarkdown(await response.text());
        document.title = `${chapter.title} | Smart Expense AI`;
        window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (error) {
        content.innerHTML = `
            <div class="error-state">
                <p>This chapter could not be rendered here.<br><a href="${chapter.file}">Open ${chapter.file}</a></p>
            </div>`;
    }
}

function openFromHash() {
    const id = window.location.hash.slice(1);
    const index = chapters.findIndex((chapter) => chapter.id === id);
    openChapter(index >= 0 ? index : 0);
}

chapterSearch.addEventListener("input", (event) => renderNavigation(event.target.value));
window.addEventListener("hashchange", openFromHash);
window.addEventListener("keydown", (event) => {
    if (event.target.matches("input, textarea")) return;
    if (event.key === "ArrowLeft" && currentIndex > 0) {
        window.location.hash = chapters[currentIndex - 1].id;
    }
    if (event.key === "ArrowRight" && currentIndex < chapters.length - 1) {
        window.location.hash = chapters[currentIndex + 1].id;
    }
});

renderNavigation();
openFromHash();
