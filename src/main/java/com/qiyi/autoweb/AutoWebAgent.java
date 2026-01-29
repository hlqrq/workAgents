package com.qiyi.autoweb;

import com.microsoft.playwright.Page;
import com.qiyi.util.LLMUtil;
import com.qiyi.util.PlayWrightUtil;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

public class AutoWebAgent {

    private static String GROOVY_SCRIPT_PROMPT_TEMPLATE = "";
    private static String REFINED_GROOVY_SCRIPT_PROMPT_TEMPLATE = "";
    private static String ACTIVE_MODEL = "DEEPSEEK";
    private static volatile JFrame AGENT_FRAME;
    private static final java.util.concurrent.atomic.AtomicBoolean BROWSER_CLOSED = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final Object PLAYWRIGHT_LOCK = new Object();

    public static void main(String[] args) {
        cleanDebugDirectory();
        cleanCacheDirectory();
        loadPrompts();
        if (args.length < 2) {
            // Default example if no args provided
            String url = ""; // No default URL, rely on Plan to navigate
            // String userPrompt = "请帮我查询“待发货”的订单。等表格加载出来后，" +
            //         "把第一页的每条记录整理成，用中文逗号分隔，内容中有回车换行的就去掉，然后逐行输出；" +
            //         "再输出页面底部显示的总记录数（比如“共xx条”）。" +
            //         "最后选中第一页第一条记录，并点击“审核推单”。";

            String userPrompt = "请帮我查询待发货所有的订单（包括多页的数据），并且输出订单的所有信息，输出格式为：\\\"列名:列内容（去掉回车换行）\\\"，然后用\\\"｜\\\"分隔，列的顺序保持表格的顺序，一条记录一行。输出以后，回到第一条订单，选中订单，然后点击审核推单，读取弹出页面的成功和失败的笔数，失败笔数大于0，页面上获取失败原因，也一起输出";

            System.out.println("No arguments provided. Running default example:");
            System.out.println("URL: " + url);
            System.out.println("Prompt: " + userPrompt);
            run(url, userPrompt);
        } else {
            if (args.length >= 3 && args[2] != null) {
                String modelArg = args[2].trim();
                String upper = modelArg.toUpperCase();
                if ("DEEPSEEK".equals(upper)) {
                    ACTIVE_MODEL = "DEEPSEEK";
                    System.out.println("Using model: DeepSeek (remote)");
                } else if ("QWEN-MAX".equals(upper) || "QWEN_MAX".equals(upper) || "ALIYUN_QWEN_MAX".equals(upper)) {
                    ACTIVE_MODEL = "QWEN_MAX";
                    System.out.println("Using model: Aliyun Qwen-Max (remote)");
                } else if ("GEMINI".equals(upper) || "GEMINI_FLASH".equals(upper)) {
                    ACTIVE_MODEL = "GEMINI";
                    System.out.println("Using model: Gemini (remote)");
                } else if ("MOONSHOT".equals(upper) || "MOONSHOT_MOONSHOT".equals(upper) || "MOONSHOT_V1".equals(upper)) {
                    ACTIVE_MODEL = "MOONSHOT";
                    System.out.println("Using model: Moonshot (remote)");
                } else if ("GLM".equals(upper) || "ZHIPU".equals(upper)) {
                    ACTIVE_MODEL = "GLM";
                    System.out.println("Using model: Zhipu GLM (remote)");
                } else if ("OLLAMA_MODEL_QWEN3_8B".equals(upper)
                        || "OLLAMA_QWEN3_8B".equals(upper)
                        || "OLLAMA".equals(upper)
                        || "QWEN3_8B".equals(upper)
                        || "QWEN3:8B".equals(upper)) {
                    ACTIVE_MODEL = "OLLAMA_QWEN3_8B";
                    System.out.println("Using local Ollama model: " + LLMUtil.OLLAMA_MODEL_QWEN3_8B + " @ " + LLMUtil.OLLAMA_HOST);
                } else {
                    ACTIVE_MODEL = "DEEPSEEK";
                    System.out.println("Unknown model arg '" + modelArg + "', defaulting to DeepSeek.");
                }
            } else {
                ACTIVE_MODEL = "DEEPSEEK";
            }
            run(args[0], args[1]);
        }
    }

    private static void cleanDebugDirectory() {
        try {
            Path debugDir = Paths.get(System.getProperty("user.dir"), "autoweb", "debug");
            if (Files.exists(debugDir)) {
                System.out.println("Cleaning debug directory: " + debugDir.toAbsolutePath());
                try (java.util.stream.Stream<Path> walk = Files.walk(debugDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            if (p == null) return;
                            if (p.equals(debugDir)) return;
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
                }
            }
            Files.createDirectories(debugDir);
        } catch (IOException e) {
            System.err.println("Warning: Failed to clean debug directory: " + e.getMessage());
        }
    }

    private static void cleanCacheDirectory() {
        try {
            Path cacheDir = Paths.get(System.getProperty("user.dir"), "autoweb", "cache");
            if (Files.exists(cacheDir)) {
                System.out.println("Cleaning cache directory: " + cacheDir.toAbsolutePath());
                try (java.util.stream.Stream<Path> walk = Files.walk(cacheDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                if (p == null) return;
                                if (p.equals(cacheDir)) return;
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                }
            }
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            System.err.println("Warning: Failed to clean cache directory: " + e.getMessage());
        }
    }

    private static void loadPrompts() {
        try {
            // Use user.dir to find the skills directory
            Path skillsDir = Paths.get(System.getProperty("user.dir"), "autoweb", "skills");
            
            Path groovyPromptPath = skillsDir.resolve("groovy_script_prompt.txt");
            if (Files.exists(groovyPromptPath)) {
                GROOVY_SCRIPT_PROMPT_TEMPLATE = new String(Files.readAllBytes(groovyPromptPath), java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("Loaded groovy_script_prompt.txt");
            } else {
                System.err.println("Warning: groovy_script_prompt.txt not found at " + groovyPromptPath.toAbsolutePath());
            }

            Path refinedPromptPath = skillsDir.resolve("refined_groovy_script_prompt.txt");
            if (Files.exists(refinedPromptPath)) {
                REFINED_GROOVY_SCRIPT_PROMPT_TEMPLATE = new String(Files.readAllBytes(refinedPromptPath), java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("Loaded refined_groovy_script_prompt.txt");
            } else {
                System.err.println("Warning: refined_groovy_script_prompt.txt not found at " + refinedPromptPath.toAbsolutePath());
            }
            
        } catch (IOException e) {
            System.err.println("Error loading prompts: " + e.getMessage());
        }
    }

    public static void run(String url, String userPrompt) {
        loadPrompts();
        PlayWrightUtil.Connection connection = PlayWrightUtil.connectAndAutomate();
        if (connection == null) {
            System.err.println("Failed to connect to browser.");
            return;
        }

        try {
            Page page = null;
            
            // If URL is provided, try to find or navigate
            if (url != null && !url.isEmpty()) {
                // Try to find if the page is already open
                for (com.microsoft.playwright.BrowserContext context : connection.browser.contexts()) {
                    for (Page p : context.pages()) {
                        if (safePageUrl(p).startsWith(url)) {
                            page = p;
                            break;
                        }
                    }
                    if (page != null) break;
                }

                if (page == null) {
                    System.out.println("Page not found, creating new page and navigating...");
                    // 优先使用现有的上下文（即用户配置目录的上下文），以保留登录态
                    if (!connection.browser.contexts().isEmpty()) {
                        page = connection.browser.contexts().get(0).newPage();
                    } else {
                        page = connection.browser.newPage();
                    }
                    page.navigate(url);
                } else {
                    System.out.println("Found existing page: " + page.title());
                    page.bringToFront();
                }

                // Check if we are on the target page, if not wait (e.g. for login)
                long maxWaitTime = 120000; // 120 seconds
                long interval = 2000; // 2 seconds
                long startTime = System.currentTimeMillis();

                while (!safePageUrl(page).startsWith(url)) {
                    if (System.currentTimeMillis() - startTime > maxWaitTime) {
                        throw new RuntimeException("Timeout waiting for target URL. Current URL: " + safePageUrl(page));
                    }
                    System.out.println("Current URL: " + safePageUrl(page) + ". Waiting for target URL: " + url + " (Login might be required)...");
                    synchronized (PLAYWRIGHT_LOCK) {
                        page.waitForTimeout(interval);
                    }
                }
            } else {
                // No URL provided - Just pick the first active page or create a blank one
                System.out.println("No target URL provided. Attaching to active page...");
                if (!connection.browser.contexts().isEmpty() && !connection.browser.contexts().get(0).pages().isEmpty()) {
                    // Pick the last used page (usually the most relevant)
                    com.microsoft.playwright.BrowserContext context = connection.browser.contexts().get(0);
                    // Use the last page as it's likely the most recently opened
                    page = context.pages().get(context.pages().size() - 1);
                    page.bringToFront();
                    System.out.println("Attached to existing page: " + page.title() + " (" + safePageUrl(page) + ")");
                } else {
                    System.out.println("No active pages found. Creating a blank new page.");
                    if (!connection.browser.contexts().isEmpty()) {
                        page = connection.browser.contexts().get(0).newPage();
                    } else {
                        page = connection.browser.newPage();
                    }
                }
            }

            boolean fastStart = (url == null || url.isEmpty());
            if (!fastStart) {
                try {
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new com.microsoft.playwright.Page.WaitForLoadStateOptions().setTimeout(10000));
                } catch (Exception e) {
                    System.out.println("Wait for NETWORKIDLE timed out or failed, continuing...");
                }

                System.out.println("Waiting 1 second for dynamic content to render...");
                page.waitForTimeout(1000);
            } else {
                System.out.println("Fast start mode: Skipping network idle wait and dynamic content wait.");
            }

            com.microsoft.playwright.Frame contentFrame = null;
            String frameName = "";
            double maxArea = 0;

            if (page.frames().size() > 1) {
                int maxScanRetries = fastStart ? 1 : 3;
                int waitBetweenScansMs = fastStart ? 0 : 1000;
                System.out.println("Checking frames (scanning up to " + maxScanRetries + " times)...");
                for (int i = 0; i < maxScanRetries; i++) {
                    maxArea = 0;
                    contentFrame = null;
                    for (com.microsoft.playwright.Frame f : page.frames()) {
                        if (f == page.mainFrame()) continue;

                        try {
                            com.microsoft.playwright.ElementHandle element = f.frameElement();
                            if (element != null) {
                                com.microsoft.playwright.options.BoundingBox box = element.boundingBox();
                                if (box != null) {
                                    double area = box.width * box.height;
                                    String frameUrl = "";
                                    try {
                                        synchronized (PLAYWRIGHT_LOCK) {
                                            frameUrl = f.url();
                                        }
                                    } catch (Exception ignored) {}
                                    System.out.println(" - [" + i + "] Frame: " + f.name() + " | URL: " + frameUrl + " | Area: " + area);

                                    if (box.width > 0 && box.height > 0) {
                                        if (area > maxArea) {
                                            maxArea = area;
                                            contentFrame = f;
                                            frameName = f.name();
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.out.println(" - Error checking frame " + f.name() + ": " + e.getMessage());
                        }
                    }

                    if (contentFrame != null) {
                        System.out.println("   -> Identified largest frame as content frame: " + frameName + " (Area: " + maxArea + ")");
                        break;
                    }

                    if (waitBetweenScansMs > 0) {
                        System.out.println("   -> No significant child frame found yet. Waiting " + (waitBetweenScansMs / 1000) + "s...");
                        page.waitForTimeout(waitBetweenScansMs);
                    }
                }
            } else {
                System.out.println("No child frames detected, skipping frame scan.");
            }

            // Launch UI
            System.out.println("Launching Control UI...");
            // Use contentFrame if available, otherwise use page
            Object executionContext = (contentFrame != null) ? contentFrame : page;
            SwingUtilities.invokeLater(() -> createGUI(executionContext, "", userPrompt, connection));

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error during initialization: " + e.getMessage());
            if (connection != null && connection.playwright != null) {
                connection.playwright.close();
            }
            System.exit(1);
        }
    }
    
    // A simple wrapper to hold the current execution context (Page or Frame)
    static class ContextWrapper {
        Object context;
        String name;
        @Override
        public String toString() {
            return name;
        }
    }

    static class ScanResult {
        java.util.List<ContextWrapper> wrappers = new java.util.ArrayList<>();
        ContextWrapper best;
    }

    static class PlanStep {
        int index;
        String description;
        String targetUrl;
        String entryAction;
        String status;
    }

    static class PlanParseResult {
        String planText;
        java.util.List<PlanStep> steps = new java.util.ArrayList<>();
        boolean confirmed;
        boolean hasQuestion;
    }

    static class HtmlSnapshot {
        int stepIndex;
        String url;
        String entryAction;
        String cacheKey;
        String cleanedHtml;
    }

    static class ModelSession {
        String userPrompt;
        String planText;
        java.util.List<PlanStep> steps = new java.util.ArrayList<>();
        java.util.Map<Integer, HtmlSnapshot> stepSnapshots = new java.util.HashMap<>();
        boolean planConfirmed;
        boolean htmlPrepared;
        String lastArtifactType;
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        String v = s.trim();
        return v.startsWith("http://") || v.startsWith("https://");
    }
    
    private static String normalizeUrlToken(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return v;
        for (int i = 0; i < 3; i++) {
            String t = v.trim();
            if (t.length() >= 2 && t.startsWith("`") && t.endsWith("`")) {
                v = t.substring(1, t.length() - 1).trim();
                continue;
            }
            if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
                v = t.substring(1, t.length() - 1).trim();
                continue;
            }
            if (t.length() >= 2 && t.startsWith("'") && t.endsWith("'")) {
                v = t.substring(1, t.length() - 1).trim();
                continue;
            }
            break;
        }
        if (v.contains("`")) v = v.replace("`", "").trim();
        return v;
    }

    private static boolean waitForUrlPrefix(Page page, String expectedPrefix, long maxWaitMs, long intervalMs, java.util.function.Consumer<String> uiLogger, String stage) {
        if (page == null) return false;
        if (expectedPrefix == null || expectedPrefix.trim().isEmpty()) return true;
        String target = expectedPrefix.trim();
        if (!looksLikeUrl(target)) return true;
        long start = System.currentTimeMillis();
        long deadline = start + Math.max(0, maxWaitMs);
        int tries = 0;
        String lastUrl = "";
        while (System.currentTimeMillis() < deadline) {
            String cur = "";
            cur = safePageUrl(page);
            if (cur == null) cur = "";
            cur = cur.trim();
            if (!cur.isEmpty() && cur.startsWith(target)) return true;

            if (uiLogger != null) {
                boolean shouldLog = tries == 0 || tries % 5 == 0 || !cur.equals(lastUrl);
                if (shouldLog) {
                    long elapsed = System.currentTimeMillis() - start;
                    long remain = Math.max(0, maxWaitMs - elapsed);
                    uiLogger.accept((stage == null ? "等待页面" : stage) + ": 当前URL=" + (cur.isEmpty() ? "(empty)" : cur) + "，等待到目标URL前缀=" + target + "（可能需要登录），剩余=" + (remain / 1000) + "s");
                }
            }
            lastUrl = cur;
            tries++;
            try {
                synchronized (PLAYWRIGHT_LOCK) {
                    page.waitForTimeout(Math.max(0, intervalMs));
                }
            } catch (Exception ignored) {}
        }
        if (uiLogger != null) {
            String cur = safePageUrl(page);
            uiLogger.accept((stage == null ? "等待页面" : stage) + ": 等待超时，未到达目标URL前缀=" + target + "，current=" + (cur == null ? "" : cur));
        }
        return false;
    }

    private static String chooseExecutionEntryUrl(ModelSession session, String currentPrompt) {
        try {
            if (session != null && session.steps != null && !session.steps.isEmpty()) {
                java.util.List<PlanStep> steps = new java.util.ArrayList<>(session.steps);
                steps.sort(java.util.Comparator.comparingInt(a -> a == null ? Integer.MAX_VALUE : a.index));
                for (PlanStep s : steps) {
                    if (s == null || s.targetUrl == null) continue;
                    String u = s.targetUrl.trim();
                    if (u.isEmpty()) continue;
                    if ("CURRENT_PAGE".equalsIgnoreCase(u)) continue;
                    if (looksLikeUrl(u)) return u;
                }
            }
            if (session != null && session.stepSnapshots != null && !session.stepSnapshots.isEmpty()) {
                java.util.List<HtmlSnapshot> snaps = new java.util.ArrayList<>(session.stepSnapshots.values());
                snaps.sort(java.util.Comparator.comparingInt(a -> a == null ? Integer.MAX_VALUE : a.stepIndex));
                for (HtmlSnapshot s : snaps) {
                    if (s == null || s.url == null) continue;
                    String u = s.url.trim();
                    if (looksLikeUrl(u)) return u;
                }
            }
            if (session != null && session.userPrompt != null) {
                String u = extractFirstUrlFromText(session.userPrompt);
                if (looksLikeUrl(u)) return u;
            }
        } catch (Exception ignored) {}
        String u = extractFirstUrlFromText(currentPrompt);
        return looksLikeUrl(u) ? u : null;
    }

    private static boolean ensureRootPageAtUrl(Page rootPage, String targetUrl, java.util.function.Consumer<String> uiLogger) {
        if (rootPage == null) return false;
        if (targetUrl == null || targetUrl.trim().isEmpty()) return false;
        String desired = targetUrl.trim();
        if (!looksLikeUrl(desired)) return false;
        
        synchronized (PLAYWRIGHT_LOCK) {
            String current = safePageUrl(rootPage);
            if (current == null) current = "";
            current = current.trim();
            boolean alreadyOk = false;
            if (!current.isEmpty() && !"about:blank".equalsIgnoreCase(current)) {
                alreadyOk = current.startsWith(desired) || desired.startsWith(current);
            }
            if (alreadyOk) {
                if (uiLogger != null) uiLogger.accept("执行前导航: 已在目标页面 | current=" + current);
                return false;
            }
            if (uiLogger != null) uiLogger.accept("执行前导航: current=" + (current.isEmpty() ? "(empty)" : current) + " -> target=" + desired);
            try {
                rootPage.bringToFront();
            } catch (Exception ignored) {}
            try {
                rootPage.navigate(desired, new Page.NavigateOptions().setTimeout(30000));
            } catch (Exception navEx) {
                if (uiLogger != null) uiLogger.accept("执行前导航失败: " + navEx.getMessage());
                return false;
            }
            try {
                rootPage.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
            } catch (Exception ignored) {
                try {
                    rootPage.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
                } catch (Exception ignored2) {}
            }
            try { rootPage.waitForTimeout(500); } catch (Exception ignored) {}
            boolean ok = waitForUrlPrefix(rootPage, desired, 120000, 2000, uiLogger, "执行前导航");
            if (!ok) {
                String cur = safePageUrl(rootPage);
                throw new RuntimeException("执行前未到达目标页面，可能需要登录。current=" + (cur == null ? "" : cur));
            }
            try {
                if (uiLogger != null) uiLogger.accept("执行前导航完成: current=" + safePageUrl(rootPage));
            } catch (Exception ignored) {}
            return true;
        }
    }

    private static ContextWrapper waitAndFindContext(Page rootPage, java.util.function.Consumer<String> uiLogger) {
        if (rootPage == null) {
            ContextWrapper fallback = new ContextWrapper();
            fallback.context = null;
            fallback.name = "Main Page";
            return fallback;
        }
        synchronized (PLAYWRIGHT_LOCK) {
            long deadline = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < deadline) {
                ContextWrapper best = selectBestContext(rootPage, null);
                if (best != null && best.name != null && !"Main Page".equals(best.name)) {
                    if (uiLogger != null) uiLogger.accept("已自动选中最佳上下文: " + best.name);
                    return best;
                }
                try { rootPage.waitForTimeout(500); } catch (Exception ignored) {}
            }
            return selectBestContext(rootPage, uiLogger);
        }
    }

    private static String extractFirstUrlFromText(String text) {
        if (text == null) return null;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("https?://[^\\s`'\"，。,）\\)]+");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                String url = m.group();
                if (url == null) return null;
                url = url.trim();
                while (!url.isEmpty()) {
                    char last = url.charAt(url.length() - 1);
                    if (last == '，' || last == '。' || last == ',' || last == '.' || last == ')' || last == '）') {
                        url = url.substring(0, url.length() - 1).trim();
                        continue;
                    }
                    break;
                }
                return url.isEmpty() ? null : url;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static java.util.LinkedHashMap<String, String> extractUrlMappingsFromText(String text) {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        if (text == null) return out;

        java.util.regex.Pattern lineLabelUrl = java.util.regex.Pattern.compile("(?m)^\\s*([^\\n:：]{1,40})\\s*[:：]\\s*(https?://[^\\s`'\"，。,）\\)]+)\\s*$");
        java.util.regex.Matcher m1 = lineLabelUrl.matcher(text);
        while (m1.find()) {
            String label = m1.group(1);
            String url = m1.group(2);
            url = sanitizeUrl(url);
            if (url == null) continue;
            label = label == null ? "" : label.trim();
            if (!label.isEmpty()) out.put(label, url);
        }

        java.util.regex.Pattern urlParenLabel = java.util.regex.Pattern.compile("(https?://[^\\s`'\"，。,）\\)]+)\\s*[（(]\\s*([^\\)）\\n]{1,40})\\s*[)）]");
        java.util.regex.Matcher m2 = urlParenLabel.matcher(text);
        while (m2.find()) {
            String url = sanitizeUrl(m2.group(1));
            String label = m2.group(2);
            if (url == null) continue;
            label = label == null ? "" : label.trim();
            if (!label.isEmpty()) out.put(label, url);
        }

        java.util.regex.Pattern urlUsedFor = java.util.regex.Pattern.compile("(https?://[^\\s`'\"，。,）\\)]+)[^\\n]{0,30}?(?:用于|用来|做|处理|完成)\\s*([^\\n，。,]{1,40})");
        java.util.regex.Matcher m3 = urlUsedFor.matcher(text);
        while (m3.find()) {
            String url = sanitizeUrl(m3.group(1));
            String label = m3.group(2);
            if (url == null) continue;
            label = label == null ? "" : label.trim();
            if (!label.isEmpty()) out.put(label, url);
        }

        java.util.regex.Pattern anyUrl = java.util.regex.Pattern.compile("https?://[^\\s`'\"，。,）\\)]+");
        java.util.regex.Matcher m4 = anyUrl.matcher(text);
        int idx = 1;
        while (m4.find()) {
            String url = sanitizeUrl(m4.group());
            if (url == null) continue;
            boolean exists = out.values().stream().anyMatch(v -> v.equals(url));
            if (exists) continue;
            out.put("URL_" + idx, url);
            idx++;
        }

        return out;
    }

    private static String sanitizeUrl(String url) {
        if (url == null) return null;
        String v = url.trim();
        while (!v.isEmpty()) {
            char last = v.charAt(v.length() - 1);
            if (last == '，' || last == '。' || last == ',' || last == '.' || last == ')' || last == '）') {
                v = v.substring(0, v.length() - 1).trim();
                continue;
            }
            break;
        }
        return v.isEmpty() ? null : v;
    }

    private static String firstQuotedToken(String s) {
        if (s == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("['\"“”](.+?)['\"“”]");
        java.util.regex.Matcher m = p.matcher(s);
        if (m.find()) {
            String t = m.group(1);
            return (t == null || t.trim().isEmpty()) ? null : t.trim();
        }
        return null;
    }

    private static PlanParseResult parsePlanFromText(String text) {
        PlanParseResult res = new PlanParseResult();
        if (text == null) return res;

        String src = text;
        int ps = src.indexOf("PLAN_START");
        int pe = src.indexOf("PLAN_END");
        if (ps >= 0 && pe > ps) {
            res.planText = src.substring(ps, pe + "PLAN_END".length());
        } else {
            res.planText = src;
        }

        String upper = src.toUpperCase();
        res.hasQuestion = upper.contains("QUESTION:");
        res.confirmed = !upper.contains("STATUS: UNKNOWN") && !res.hasQuestion;

        // Robust Step Header: Case-insensitive, handles **, handles Chinese/English colon
        java.util.regex.Pattern stepHeader = java.util.regex.Pattern.compile("(?mi)^\\s*\\**Step\\s+(\\d+)\\**[:：]?\\s*$");
        java.util.regex.Matcher mh = stepHeader.matcher(src);
        java.util.List<Integer> stepStarts = new java.util.ArrayList<>();
        java.util.List<Integer> stepNums = new java.util.ArrayList<>();
        while (mh.find()) {
            stepStarts.add(mh.start());
            try {
                stepNums.add(Integer.parseInt(mh.group(1)));
            } catch (Exception ignored) {
                stepNums.add(stepNums.size() + 1);
            }
        }
        if (stepNums.isEmpty()) {
            res.confirmed = false;
            return res;
        }
        stepStarts.add(src.length());

        for (int i = 0; i < stepNums.size(); i++) {
            int start = stepStarts.get(i);
            int end = stepStarts.get(i + 1);
            String block = src.substring(start, Math.min(end, src.length()));

            PlanStep step = new PlanStep();
            step.index = stepNums.get(i);
            // Robust Field Matching: Case-insensitive, handles **, handles Chinese/English colon
            step.description = matchFirst(block, "(?mi)^\\s*-\\s*\\**Description\\**\\s*[:：]\\s*(.*)$");
            step.targetUrl = matchFirst(block, "(?mi)^\\s*-\\s*\\**Target\\s+URL\\**\\s*[:：]\\s*(.*)$");
            step.entryAction = matchFirst(block, "(?mi)^\\s*-\\s*\\**Entry\\s+Point\\s+Action\\**\\s*[:：]\\s*(.*)$");
            step.status = matchFirst(block, "(?mi)^\\s*-\\s*\\**Status\\**\\s*[:：]\\s*(.*)$");
            
            if (step.targetUrl != null) {
                step.targetUrl = step.targetUrl.trim().replaceAll("[`*]", "");
            }
            if (step.entryAction != null) step.entryAction = step.entryAction.trim();
            if (step.status != null) step.status = step.status.trim();
            res.steps.add(step);
        }

        if (!res.steps.isEmpty()) {
            boolean anyUnknown = false;
            for (PlanStep s : res.steps) {
                if (s.status != null && s.status.toUpperCase().contains("UNKNOWN")) {
                    anyUnknown = true;
                    break;
                }
            }
            res.confirmed = res.confirmed && !anyUnknown;
        }

        return res;
    }
    
    private static java.util.List<String> inferMissingEntryLabels(java.util.List<String> modelNames, java.util.Map<String, ModelSession> sessionsByModel) {
        java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
        if (modelNames == null || modelNames.isEmpty() || sessionsByModel == null) {
            return new java.util.ArrayList<>(labels);
        }
        for (String model : modelNames) {
            if (model == null) continue;
            ModelSession session = sessionsByModel.get(model);
            if (session == null || session.steps == null) continue;
            for (PlanStep step : session.steps) {
                if (step == null) continue;
                String status = step.status == null ? "" : step.status.trim().toUpperCase();
                String targetUrl = step.targetUrl == null ? "" : step.targetUrl.trim();
                String entryAction = step.entryAction == null ? "" : step.entryAction.trim();

                boolean needs = false;
                if (!status.isEmpty() && status.contains("UNKNOWN")) needs = true;
                if (targetUrl.isEmpty()) needs = true;
                String targetUpper = targetUrl.toUpperCase();
                if (targetUpper.contains("UNKNOWN")) needs = true;
                if (targetUrl.startsWith("[") && targetUrl.endsWith("]")) needs = true;
                if (targetUrl.contains("用户") && targetUpper.contains("URL")) needs = true;
                if (!needs) {
                    String ea = entryAction.toUpperCase();
                    if (ea.contains("POPUP") || ea.contains("INPUT")) {
                        if (!looksLikeUrl(targetUrl) && !"CURRENT_PAGE".equalsIgnoreCase(targetUrl)) {
                            needs = true;
                        }
                    }
                }
                if (!needs) continue;

                String label = null;
                String desc = step.description == null ? "" : step.description.trim();
                if (!desc.isEmpty()) {
                    if (desc.startsWith("[") && desc.endsWith("]") && desc.length() > 2) {
                        desc = desc.substring(1, desc.length() - 1).trim();
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9_\\-]{2,40}页面)").matcher(desc);
                    if (m.find()) label = m.group(1);
                }
                if ((label == null || label.trim().isEmpty()) && !desc.isEmpty()) {
                    label = desc;
                }
                if (label == null || label.trim().isEmpty()) {
                    label = "Step " + step.index + " 入口地址";
                }
                label = label.trim();
                if (!label.isEmpty()) labels.add(label);
            }
        }
        return new java.util.ArrayList<>(labels);
    }

    private static String buildEntryInputHint(java.util.List<String> needModels, java.util.Map<String, ModelSession> sessionsByModel) {
        java.util.List<String> labels = inferMissingEntryLabels(needModels, sessionsByModel);
        StringBuilder sb = new StringBuilder();
        sb.append("需要补充以下操作入口地址（单个地址一行）：\n");
        if (labels.isEmpty()) {
            sb.append("- 入口地址（示例：订单管理页面）\n");
        } else {
            for (String l : labels) {
                sb.append("- ").append(l).append("\n");
            }
        }
        sb.append("\n请按上述名称逐行输入（推荐格式：名称: URL）：\n");
        if (!labels.isEmpty()) {
            int n = Math.min(6, labels.size());
            for (int i = 0; i < n; i++) {
                sb.append(labels.get(i)).append(": https://\n");
            }
        } else {
            sb.append("订单管理页面: https://\n");
        }
        if (needModels != null && !needModels.isEmpty()) {
            sb.append("\n影响模型: ").append(String.join("，", needModels));
        }
        return sb.toString();
    }

    private static String matchFirst(String src, String regex) {
        if (src == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher m = p.matcher(src);
        if (m.find()) {
            String v = m.group(1);
            return v == null ? null : v.trim();
        }
        return null;
    }

    private static String sha256Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest((s == null ? "" : s).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf((s == null ? "" : s).hashCode());
        }
    }

    private static long utf8Bytes(String s) {
        if (s == null) return 0L;
        try {
            return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            return s.length();
        }
    }
    
    private static String stackTraceToString(Throwable t) {
        if (t == null) return "";
        try {
            java.io.StringWriter sw = new java.io.StringWriter(2048);
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Exception e) {
            return String.valueOf(t);
        }
    }

    private static void logRequestBytes(
            String stageName,
            String modelName,
            String mode,
            String payload,
            String prompt,
            java.util.function.Consumer<String> uiLogger
    ) {
        if (uiLogger == null) return;
        long payloadBytes = utf8Bytes(payload);
        long promptBytes = utf8Bytes(prompt);
        uiLogger.accept("请求发起: stage=" + (stageName == null ? "" : stageName)
                + ", model=" + (modelName == null ? "" : modelName)
                + ", mode=" + (mode == null ? "" : mode)
                + ", send=prompt"
                + ", bytes(prompt)=" + promptBytes
                + ", bytes(payload)=" + payloadBytes);
    }

    private static java.nio.file.Path ensureCacheDir() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Paths.get(System.getProperty("user.dir"), "autoweb", "cache");
        if (!java.nio.file.Files.exists(dir)) {
            java.nio.file.Files.createDirectories(dir);
        }
        return dir;
    }

    private static java.nio.file.Path ensureDebugDir() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Paths.get(System.getProperty("user.dir"), "autoweb", "debug");
        if (!java.nio.file.Files.exists(dir)) {
            java.nio.file.Files.createDirectories(dir);
        }
        return dir;
    }

    private static String newDebugTimestamp() {
        try {
            return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private static String sanitizeFileToken(String s) {
        if (s == null) return "UNKNOWN";
        String v = s.trim();
        if (v.isEmpty()) return "UNKNOWN";
        v = v.replaceAll("\\s+", "_");
        v = v.replaceAll("[^A-Za-z0-9._-]", "_");
        while (v.contains("__")) v = v.replace("__", "_");
        if (v.length() > 64) v = v.substring(0, 64);
        return v.isEmpty() ? "UNKNOWN" : v;
    }

    private static String truncateForDebug(String s, int maxChars) {
        if (s == null) return "";
        if (maxChars <= 0) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "...(truncated)";
    }

    private static String redactForDebug(String s) {
        if (s == null || s.isEmpty()) return s;
        String out = s;
        out = out.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)([^\\s\"']+)", "$1***REDACTED***");
        out = out.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*['\"])([^'\"]+)(['\"])", "$1***REDACTED***$3");
        out = out.replaceAll("(?i)(token\\s*[:=]\\s*)([^\\s\"']+)", "$1***REDACTED***");
        out = out.replaceAll("(?i)(token\\s*[:=]\\s*['\"])([^'\"]+)(['\"])", "$1***REDACTED***$3");
        out = out.replaceAll("(?i)(secret\\s*[:=]\\s*)([^\\s\"']+)", "$1***REDACTED***");
        out = out.replaceAll("(?i)(secret\\s*[:=]\\s*['\"])([^'\"]+)(['\"])", "$1***REDACTED***$3");
        out = out.replaceAll("(?i)(Authorization\\s*[:=]\\s*Bearer\\s+)([A-Za-z0-9._-]+)", "$1***REDACTED***");
        return out;
    }

    private static String saveDebugArtifact(
            String ts,
            String modelName,
            String mode,
            String kind,
            String content,
            java.util.function.Consumer<String> uiLogger
    ) {
        try {
            java.nio.file.Path dir = ensureDebugDir();
            String fileName = sanitizeFileToken(ts) + "_" +
                    sanitizeFileToken(modelName) + "_" +
                    sanitizeFileToken(mode) + "_" +
                    sanitizeFileToken(kind) + ".txt";
            java.nio.file.Path path = dir.resolve(fileName);

            String processed = truncateForDebug(redactForDebug(content), 2_000_000);
            String sha = sha256Hex(processed);

            StringBuilder sb = new StringBuilder();
            sb.append("TS: ").append(ts == null ? "" : ts).append("\n");
            sb.append("MODEL: ").append(modelName == null ? "" : modelName).append("\n");
            sb.append("MODE: ").append(mode == null ? "" : mode).append("\n");
            sb.append("KIND: ").append(kind == null ? "" : kind).append("\n");
            sb.append("LEN: ").append(processed.length()).append("\n");
            sb.append("SHA256: ").append(sha).append("\n");
            sb.append("----\n");
            sb.append(processed);

            java.nio.file.Files.write(path, sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return path.toAbsolutePath().toString();
        } catch (Exception e) {
            if (uiLogger != null) uiLogger.accept("Debug artifact save failed: " + e.getMessage());
            return null;
        }
    }

    private static void logPayloadSummary(String payload, java.util.function.Consumer<String> uiLogger) {
        if (uiLogger == null) return;
        if (payload == null) {
            uiLogger.accept("Payload Summary: (null)");
            return;
        }
        String mode = extractModeFromPayload(payload);
        String currentUrl = matchFirst(payload, "(?m)^\\s*CURRENT_PAGE_URL\\s*:\\s*(.*)$");
        String userUrl = matchFirst(payload, "(?m)^\\s*USER_PROVIDED_URL\\s*:\\s*(.*)$");
        boolean samePage = payload.contains("SAME_PAGE_OPERATION: true");
        int urlListCount = 0;
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^\\s*-\\s+.+$");
            java.util.regex.Pattern listHeader = java.util.regex.Pattern.compile("(?m)^\\s*USER_PROVIDED_URLS\\s*:\\s*$");
            java.util.regex.Matcher h = listHeader.matcher(payload);
            int listStart = -1;
            if (h.find()) listStart = h.end();
            if (listStart >= 0) {
                String tail = payload.substring(listStart);
                java.util.regex.Matcher mm = p.matcher(tail);
                while (mm.find()) {
                    urlListCount++;
                }
            }
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder();
        sb.append("Payload Summary: ");
        sb.append("MODE=").append(mode == null ? "" : mode);
        if (currentUrl != null && !currentUrl.isEmpty()) sb.append(", CURRENT_PAGE_URL=").append(currentUrl);
        if (userUrl != null && !userUrl.isEmpty()) sb.append(", USER_PROVIDED_URL=").append(userUrl);
        if (urlListCount > 0) sb.append(", USER_PROVIDED_URLS=").append(urlListCount);
        if (samePage) sb.append(", SAME_PAGE_OPERATION=true");
        uiLogger.accept(sb.toString());
    }

    private static HtmlSnapshot readCachedHtml(int stepIndex, String url, String entryAction) {
        try {
            java.nio.file.Path dir = ensureCacheDir();
            String key = sha256Hex((url == null ? "" : url) + "\n" + (entryAction == null ? "" : entryAction));
            java.nio.file.Path cleanedPath = dir.resolve(key + ".cleaned.html");
            if (!java.nio.file.Files.exists(cleanedPath)) return null;
            HtmlSnapshot snap = new HtmlSnapshot();
            snap.stepIndex = stepIndex;
            snap.url = url;
            snap.entryAction = entryAction;
            snap.cacheKey = key;
            snap.cleanedHtml = new String(java.nio.file.Files.readAllBytes(cleanedPath), java.nio.charset.StandardCharsets.UTF_8);
            return snap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HtmlSnapshot writeCachedHtml(int stepIndex, String url, String entryAction, String rawHtml, String cleanedHtml) {
        try {
            java.nio.file.Path dir = ensureCacheDir();
            String key = sha256Hex((url == null ? "" : url) + "\n" + (entryAction == null ? "" : entryAction));
            java.nio.file.Path rawPath = dir.resolve(key + ".raw.html");
            java.nio.file.Path cleanedPath = dir.resolve(key + ".cleaned.html");
            java.nio.file.Files.write(rawPath, (rawHtml == null ? "" : rawHtml).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(cleanedPath, (cleanedHtml == null ? "" : cleanedHtml).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            HtmlSnapshot snap = new HtmlSnapshot();
            snap.stepIndex = stepIndex;
            snap.url = url;
            snap.entryAction = entryAction;
            snap.cacheKey = key;
            snap.cleanedHtml = cleanedHtml;
            return snap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safePageUrl(Page page) {
        if (page == null) return "";
        try {
            synchronized (PLAYWRIGHT_LOCK) {
                String u = page.url();
                if (u == null) return "";
                return u.trim();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String buildPlanOnlyPayload(Page currentPage, String userPrompt) {
        return buildPlanOnlyPayload(safePageUrl(currentPage), userPrompt);
    }

    private static String buildPlanOnlyPayload(String currentUrl, String userPrompt) {
        String userProvidedUrl = extractFirstUrlFromText(userPrompt);
        java.util.LinkedHashMap<String, String> urlMappings = extractUrlMappingsFromText(userPrompt);
        boolean samePageOperation = false;
        if (userPrompt != null) {
            String t = userPrompt;
            samePageOperation =
                    t.contains("所有的任务都是这个页面") ||
                    t.contains("不是独立的入口") ||
                    t.contains("不需要独立的入口") ||
                    t.contains("不独立的入口");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("MODE: PLAN_ONLY\n");
        if (currentUrl != null && !currentUrl.isEmpty()) {
            sb.append("CURRENT_PAGE_URL: ").append(currentUrl).append("\n");
        }
        if (userProvidedUrl != null && !userProvidedUrl.isEmpty()) {
            sb.append("USER_PROVIDED_URL: ").append(userProvidedUrl).append("\n");
        }
        if (!urlMappings.isEmpty()) {
            sb.append("USER_PROVIDED_URLS:\n");
            for (java.util.Map.Entry<String, String> e : urlMappings.entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().trim();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (v.isEmpty()) continue;
                if (k.isEmpty()) sb.append("- ").append(v).append("\n");
                else sb.append("- ").append(k).append(": ").append(v).append("\n");
            }
        }
        if (samePageOperation) {
            sb.append("SAME_PAGE_OPERATION: true\n");
        }
        return sb.toString();
    }

    private static String buildPlanEntryPayload(Page currentPage, String userPrompt) {
        return buildPlanEntryPayload(safePageUrl(currentPage), userPrompt);
    }

    /**
     * 构建初始计划生成的 Payload (核心逻辑)
     * 1. 解析用户任务中的 URL (extractFirstUrlFromText / extractUrlMappingsFromText)
     * 2. 检测是否为"当前页面操作" (samePageOperation)
     * 3. 组装 Context Payload 字符串，标记 MODE: PLAN_ENTRY
     */
    private static String buildPlanEntryPayload(String currentUrl, String userPrompt) {
        // 1. 尝试从 Prompt 中提取 URL
        String userProvidedUrl = extractFirstUrlFromText(userPrompt);
        java.util.LinkedHashMap<String, String> urlMappings = extractUrlMappingsFromText(userPrompt);
        boolean samePageOperation = false;
        
        // 2. 检测是否暗示在当前页面操作
        if (userPrompt != null) {
            String t = userPrompt;
            samePageOperation =
                    t.contains("所有的任务都是这个页面") ||
                    t.contains("不是独立的入口") ||
                    t.contains("不需要独立的入口") ||
                    t.contains("不独立的入口");
        }

        // 3. 组装 Payload 文本
        StringBuilder sb = new StringBuilder();
        sb.append("MODE: PLAN_ENTRY\n");
        if (currentUrl != null && !currentUrl.isEmpty()) {
            sb.append("CURRENT_PAGE_URL: ").append(currentUrl).append("\n");
        }
        if (userProvidedUrl != null && !userProvidedUrl.isEmpty()) {
            sb.append("USER_PROVIDED_URL: ").append(userProvidedUrl).append("\n");
        }
        if (!urlMappings.isEmpty()) {
            sb.append("USER_PROVIDED_URLS:\n");
            for (java.util.Map.Entry<String, String> e : urlMappings.entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().trim();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (v.isEmpty()) continue;
                if (k.isEmpty()) sb.append("- ").append(v).append("\n");
                else sb.append("- ").append(k).append(": ").append(v).append("\n");
            }
        }
        if (samePageOperation) {
            sb.append("SAME_PAGE_OPERATION: true\n");
        }
        return sb.toString();
    }

    private static String buildPlanRefinePayload(Page currentPage, String userPrompt, String refineHint) {
        return buildPlanRefinePayload(safePageUrl(currentPage), userPrompt, refineHint);
    }

    /**
     * 构建计划修正/入口补充的 Payload (核心逻辑)
     * 1. 继承原 Prompt 中的 URL 映射
     * 2. 解析用户补充输入 (refineHint) 中的新 URL 并合并
     * 3. 组装 Context Payload 字符串，标记 MODE: PLAN_REFINE，并包含 USER_INPUT_RAW
     */
    private static String buildPlanRefinePayload(String currentUrl, String userPrompt, String refineHint) {
        // 1. 合并 URL 映射 (原 Prompt + 补充输入)
        java.util.LinkedHashMap<String, String> urlMappings = new java.util.LinkedHashMap<>();
        urlMappings.putAll(extractUrlMappingsFromText(userPrompt));
        java.util.LinkedHashMap<String, String> hintMappings = extractUrlMappingsFromText(refineHint);
        for (java.util.Map.Entry<String, String> e : hintMappings.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (v == null || v.trim().isEmpty()) continue;
            boolean exists = urlMappings.values().stream().anyMatch(x -> x.equals(v.trim()));
            if (!exists) {
                urlMappings.put(k, v.trim());
            }
        }

        boolean samePageOperation = false;
        String t = (refineHint == null ? "" : refineHint) + "\n" + (userPrompt == null ? "" : userPrompt);
        samePageOperation =
                t.contains("所有的任务都是这个页面") ||
                t.contains("不是独立的入口") ||
                t.contains("不需要独立的入口") ||
                t.contains("不独立的入口");

        // 2. 组装 Payload
        StringBuilder sb = new StringBuilder();
        sb.append("MODE: PLAN_REFINE\n");
        if (!currentUrl.isEmpty()) sb.append("CURRENT_PAGE_URL: ").append(currentUrl).append("\n");
        
        String userProvidedUrl = extractFirstUrlFromText(refineHint);
        if (userProvidedUrl != null && !userProvidedUrl.isEmpty()) {
            sb.append("USER_PROVIDED_URL: ").append(userProvidedUrl).append("\n");
        }
        
        // 关键: 将原始输入透传给 LLM，防止正则提取失败
        if (refineHint != null && !refineHint.trim().isEmpty()) {
            sb.append("USER_INPUT_RAW: ").append(refineHint.trim()).append("\n");
        }

        if (!urlMappings.isEmpty()) {
            sb.append("USER_PROVIDED_URLS:\n");
            for (java.util.Map.Entry<String, String> e : urlMappings.entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().trim();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (v.isEmpty()) continue;
                if (k.isEmpty()) sb.append("- ").append(v).append("\n");
                else sb.append("- ").append(k).append(": ").append(v).append("\n");
            }
        }
        if (samePageOperation) sb.append("SAME_PAGE_OPERATION: true\n");
        return sb.toString();
    }

    private static String buildCodegenPayload(Page currentPage, String planText, java.util.List<HtmlSnapshot> snapshots) {
        String currentUrl = safePageUrl(currentPage);
        if (currentUrl == null) currentUrl = "";
        currentUrl = currentUrl.trim();
        if (currentUrl.isEmpty() || "about:blank".equalsIgnoreCase(currentUrl)) {
            if (snapshots != null) {
                for (HtmlSnapshot s : snapshots) {
                    if (s == null || s.url == null) continue;
                    String u = s.url.trim();
                    if (looksLikeUrl(u)) {
                        currentUrl = u;
                        break;
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("MODE: CODEGEN\n");
        if (!currentUrl.isEmpty()) {
            sb.append("CURRENT_PAGE_URL: ").append(currentUrl).append("\n");
        }
        sb.append("PLAN:\n").append(planText == null ? "" : planText).append("\n");
        sb.append("STEP_HTMLS_CLEANED:\n");
        appendStepHtmlsCleaned(sb, snapshots, 500000);
        return sb.toString();
    }

    private static void appendStepHtmlsCleaned(StringBuilder sb, java.util.List<HtmlSnapshot> snapshots, int maxChars) {
        if (sb == null) return;
        if (snapshots == null || snapshots.isEmpty()) return;
        int used = 0;
        java.util.HashMap<String, Integer> firstStepByUrl = new java.util.HashMap<>();
        for (HtmlSnapshot snap : snapshots) {
            if (snap == null || snap.cleanedHtml == null) continue;
            String urlKey = "";
            try { urlKey = (snap.url == null ? "" : snap.url.trim()); } catch (Exception ignored) {}
            String header = "[Step " + snap.stepIndex + "] URL: " + (snap.url == null ? "" : snap.url) + " | Entry: " + (snap.entryAction == null ? "" : snap.entryAction) + "\n";
            Integer first = urlKey.isEmpty() ? null : firstStepByUrl.get(urlKey);
            if (first != null && first.intValue() != snap.stepIndex) {
                String body = "DUPLICATE_URL: SAME_AS_STEP " + first + "\n";
                int remaining = maxChars - used - header.length();
                if (remaining <= 0) break;
                if (body.length() > remaining) {
                    body = body.substring(0, remaining) + "...(truncated)";
                }
                sb.append(header).append(body).append("\n");
                used += header.length() + body.length() + 1;
                if (used >= maxChars) break;
                continue;
            }
            if (!urlKey.isEmpty() && !firstStepByUrl.containsKey(urlKey)) {
                firstStepByUrl.put(urlKey, snap.stepIndex);
            }
            String body = snap.cleanedHtml;
            int remaining = maxChars - used - header.length();
            if (remaining <= 0) break;
            if (body.length() > remaining) {
                body = body.substring(0, remaining) + "...(truncated)";
            }
            sb.append(header).append(body).append("\n");
            used += header.length() + body.length() + 1;
            if (used >= maxChars) break;
        }
    }

    private static String buildRefinePayload(Page currentPage, String planText, java.util.List<HtmlSnapshot> snapshots, String currentCleanedHtml, String userPrompt, String refineHint) {
        String currentUrl = safePageUrl(currentPage);
        if (currentUrl == null) currentUrl = "";
        currentUrl = currentUrl.trim();
        if (currentUrl.isEmpty() || "about:blank".equalsIgnoreCase(currentUrl)) {
            if (snapshots != null) {
                for (HtmlSnapshot s : snapshots) {
                    if (s == null || s.url == null) continue;
                    String u = s.url.trim();
                    if (looksLikeUrl(u)) {
                        currentUrl = u;
                        break;
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("MODE: REFINE_CODE\n");
        if (!currentUrl.isEmpty()) {
            sb.append("CURRENT_PAGE_URL: ").append(currentUrl).append("\n");
        }

        java.util.LinkedHashMap<String, String> urlMappings = new java.util.LinkedHashMap<>();
        urlMappings.putAll(extractUrlMappingsFromText(userPrompt));
        java.util.LinkedHashMap<String, String> hintMappings = extractUrlMappingsFromText(refineHint);
        for (java.util.Map.Entry<String, String> e : hintMappings.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (v == null || v.trim().isEmpty()) continue;
            boolean exists = urlMappings.values().stream().anyMatch(x -> x.equals(v.trim()));
            if (!exists) {
                urlMappings.put(k, v.trim());
            }
        }
        if (!urlMappings.isEmpty()) {
            sb.append("USER_PROVIDED_URLS:\n");
            for (java.util.Map.Entry<String, String> e : urlMappings.entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey().trim();
                String v = e.getValue() == null ? "" : e.getValue().trim();
                if (v.isEmpty()) continue;
                if (k.isEmpty()) sb.append("- ").append(v).append("\n");
                else sb.append("- ").append(k).append(": ").append(v).append("\n");
            }
        }

        if (currentCleanedHtml != null && !currentCleanedHtml.isEmpty()) {
            String v = currentCleanedHtml;
            if (v.length() > 200000) v = v.substring(0, 200000) + "...(truncated)";
            sb.append("CURRENT_PAGE_HTML_CLEANED:\n").append(v).append("\n");
        }
        sb.append("PLAN:\n").append(planText == null ? "" : planText).append("\n");
        sb.append("STEP_HTMLS_CLEANED:\n");
        appendStepHtmlsCleaned(sb, snapshots, 500000);
        return sb.toString();
    }

    private static java.util.List<HtmlSnapshot> prepareStepHtmls(
            Page rootPage,
            java.util.List<PlanStep> steps,
            java.util.function.Consumer<String> uiLogger
    ) {
        if (steps == null || steps.isEmpty()) return new java.util.ArrayList<>();
        synchronized (PLAYWRIGHT_LOCK) {
            java.util.List<HtmlSnapshot> out = new java.util.ArrayList<>();
            JFrame f = AGENT_FRAME;
            java.util.concurrent.atomic.AtomicInteger prevState = new java.util.concurrent.atomic.AtomicInteger(Frame.NORMAL);
            java.util.concurrent.atomic.AtomicInteger prevExtendedState = new java.util.concurrent.atomic.AtomicInteger(Frame.NORMAL);
            java.util.concurrent.atomic.AtomicBoolean hadFrame = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicBoolean minimizedByAgent = new java.util.concurrent.atomic.AtomicBoolean(false);
            if (f != null) {
                hadFrame.set(true);
                try { prevState.set(f.getState()); } catch (Exception ignored) {}
                try { prevExtendedState.set(f.getExtendedState()); } catch (Exception ignored) {}
            }

            java.util.HashMap<String, HtmlSnapshot> snapshotByUrl = new java.util.HashMap<>();
            com.microsoft.playwright.BrowserContext ctx = rootPage.context();
            Page tmp = null;
            java.util.List<Page> openedPages = new java.util.ArrayList<>();
            String rootUrl = safePageUrl(rootPage);
            try {
                tmp = ctx.newPage();
                openedPages.add(tmp);
                for (PlanStep step : steps) {
                    if (step == null) continue;

                    String url = normalizeUrlToken(step.targetUrl);
                    boolean isCurrentPage = (url == null || url.trim().isEmpty() || "CURRENT_PAGE".equalsIgnoreCase(url.trim()));
                    if (isCurrentPage) {
                        url = rootUrl;
                    }
                    url = normalizeUrlToken(url);
                    if (!looksLikeUrl(url)) {
                        HtmlSnapshot cached = readCachedHtml(step.index, url, step.entryAction);
                        if (cached != null) out.add(cached);
                        continue;
                    }

                    String urlKey = url == null ? "" : url.trim();
                    HtmlSnapshot existing = urlKey.isEmpty() ? null : snapshotByUrl.get(urlKey);
                    if (existing != null) {
                        if (uiLogger != null) uiLogger.accept("复用已采集页面: Step " + step.index + " | " + url + " | sameAsStep=" + existing.stepIndex);
                        HtmlSnapshot clone = new HtmlSnapshot();
                        clone.stepIndex = step.index;
                        clone.url = existing.url;
                        clone.entryAction = step.entryAction;
                        clone.cacheKey = existing.cacheKey;
                        clone.cleanedHtml = existing.cleanedHtml;
                        out.add(clone);
                        continue;
                    }

                    if (isCurrentPage) {
                        HtmlSnapshot cached = readCachedHtml(step.index, url, step.entryAction);
                        if (cached != null) {
                            if (uiLogger != null) uiLogger.accept("命中缓存: Step " + step.index + " | " + url);
                            if (!urlKey.isEmpty()) snapshotByUrl.putIfAbsent(urlKey, cached);
                            out.add(cached);
                            continue;
                        }

                        if (hadFrame.get() && f != null && minimizedByAgent.compareAndSet(false, true)) {
                            SwingUtilities.invokeLater(() -> {
                                try { f.setExtendedState(prevExtendedState.get() | Frame.ICONIFIED); } catch (Exception ignored) {}
                            });
                        }
                        if (uiLogger != null) uiLogger.accept("采集当前页面: Step " + step.index + " | " + url);
                        String rawHtml = "";
                        try {
                            ContextWrapper best = null;
                            for (int attempt = 0; attempt < 6; attempt++) {
                                ScanResult sr = scanContexts(rootPage);
                                if (sr != null) best = sr.best;
                                if (best != null && best.name != null && !"Main Page".equals(best.name)) break;
                                try { rootPage.waitForTimeout(500); } catch (Exception ignored) {}
                            }
                            if (best != null) {
                                String ctxUrl = "";
                                try {
                                    if (best.context instanceof com.microsoft.playwright.Frame) {
                                        ctxUrl = ((com.microsoft.playwright.Frame) best.context).url();
                                    } else if (best.context instanceof Page) {
                                        ctxUrl = ((Page) best.context).url();
                                    }
                                } catch (Exception ignored) {}
                                if (uiLogger != null) {
                                    uiLogger.accept("采集 HTML 上下文: Step " + step.index + " | " + best.name + (ctxUrl == null || ctxUrl.trim().isEmpty() ? "" : " | " + ctxUrl.trim()));
                                }
                                rawHtml = getPageContent(best.context);
                            } else {
                                rawHtml = rootPage.content();
                            }
                        } catch (Exception ignored) {
                            try { rawHtml = rootPage.content(); } catch (Exception ignored2) {}
                        }
                        String cleaned = HTMLCleaner.clean(rawHtml);
                        if (cleaned.length() > 500000) cleaned = cleaned.substring(0, 500000) + "...(truncated)";
                        HtmlSnapshot snap = writeCachedHtml(step.index, url, step.entryAction, rawHtml, cleaned);
                        if (snap != null) {
                            if (!urlKey.isEmpty()) snapshotByUrl.putIfAbsent(urlKey, snap);
                            out.add(snap);
                        }
                        continue;
                    }

                    HtmlSnapshot cached = readCachedHtml(step.index, url, step.entryAction);
                    if (cached != null) {
                        if (uiLogger != null) uiLogger.accept("命中缓存: Step " + step.index + " | " + url);
                        if (!urlKey.isEmpty()) snapshotByUrl.putIfAbsent(urlKey, cached);
                        out.add(cached);
                        continue;
                    }

                    if (hadFrame.get() && f != null && minimizedByAgent.compareAndSet(false, true)) {
                        SwingUtilities.invokeLater(() -> {
                            try { f.setExtendedState(prevExtendedState.get() | Frame.ICONIFIED); } catch (Exception ignored) {}
                        });
                    }
                    if (uiLogger != null) uiLogger.accept("采集页面: Step " + step.index + " | " + url);
                    try {
                        tmp.navigate(url);
                    } catch (Exception navEx) {
                        if (uiLogger != null) uiLogger.accept("打开 URL 失败: " + navEx.getMessage());
                    }
                    try {
                        tmp.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
                    } catch (Exception ignored) {
                        try { tmp.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD); } catch (Exception ignored2) {}
                    }
                    boolean stepOk = waitForUrlPrefix(tmp, url, 120000, 2000, uiLogger, "采集页面 Step " + step.index);
                    if (!stepOk) {
                        continue;
                    }

                    String entry = step.entryAction == null ? "" : step.entryAction;
                    String token = firstQuotedToken(entry);
                    if (token != null) {
                        try {
                            com.microsoft.playwright.Locator loc = tmp.locator("text=" + token).first();
                            if (loc != null) {
                                boolean mayOpenNew = entry.contains("新开") || entry.toLowerCase().contains("new tab") || entry.toLowerCase().contains("new window");
                                if (mayOpenNew) {
                                    try {
                                        Page newPage = tmp.context().waitForPage(
                                                new com.microsoft.playwright.BrowserContext.WaitForPageOptions().setTimeout(5000),
                                                () -> loc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000))
                                        );
                                        if (newPage != null) {
                                            Page old = tmp;
                                            tmp = newPage;
                                            openedPages.add(tmp);
                                            try { old.close(); } catch (Exception ignored) {}
                                            tmp.waitForLoadState();
                                        }
                                    } catch (Exception e) {
                                        try { loc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignored) {}
                                    }
                                } else {
                                    try { loc.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignored) {}
                                    try {
                                        tmp.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
                                    } catch (Exception ignored2) {}
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    String rawHtml = "";
                    try {
                        ContextWrapper best = null;
                        for (int attempt = 0; attempt < 6; attempt++) {
                            ScanResult sr = scanContexts(tmp);
                            if (sr != null) best = sr.best;
                            if (best != null && best.name != null && !"Main Page".equals(best.name)) break;
                            try { tmp.waitForTimeout(500); } catch (Exception ignored) {}
                        }
                        if (best != null) {
                            String ctxUrl = "";
                            try {
                                if (best.context instanceof com.microsoft.playwright.Frame) {
                                    ctxUrl = ((com.microsoft.playwright.Frame) best.context).url();
                                } else if (best.context instanceof Page) {
                                    ctxUrl = ((Page) best.context).url();
                                }
                            } catch (Exception ignored) {}
                            if (uiLogger != null) {
                                uiLogger.accept("采集 HTML 上下文: Step " + step.index + " | " + best.name + (ctxUrl == null || ctxUrl.trim().isEmpty() ? "" : " | " + ctxUrl.trim()));
                            }
                            rawHtml = getPageContent(best.context);
                        } else {
                            rawHtml = tmp.content();
                        }
                    } catch (Exception ignored) {
                        try { rawHtml = tmp.content(); } catch (Exception ignored2) {}
                    }
                    String cleaned = HTMLCleaner.clean(rawHtml);
                    if (cleaned.length() > 500000) cleaned = cleaned.substring(0, 500000) + "...(truncated)";
                    HtmlSnapshot snap = writeCachedHtml(step.index, url, step.entryAction, rawHtml, cleaned);
                    if (snap != null) {
                        if (!urlKey.isEmpty()) snapshotByUrl.putIfAbsent(urlKey, snap);
                        out.add(snap);
                    }
                }
            } finally {
                for (int i = openedPages.size() - 1; i >= 0; i--) {
                    Page p = openedPages.get(i);
                    if (p == null) continue;
                    try { p.close(); } catch (Exception ignored) {}
                }
                if (hadFrame.get() && f != null && minimizedByAgent.get()) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            int st = prevExtendedState.get();
                            f.setExtendedState(st);
                            if (!f.isVisible()) f.setVisible(true);
                        } catch (Exception ignored) {}
                    });
                }
            }
            return out;
        }
    }

    private static ScanResult scanContexts(Page page) {
        synchronized (PLAYWRIGHT_LOCK) {
            ScanResult result = new ScanResult();

            ContextWrapper mainPageWrapper = new ContextWrapper();
            mainPageWrapper.context = page;
            mainPageWrapper.name = "Main Page";
            result.wrappers.add(mainPageWrapper);
            result.best = mainPageWrapper;

            double maxArea = 0;

            System.out.println("Scanning frames...");
            ContextWrapper firstFrame = null;
            for (com.microsoft.playwright.Frame f : page.frames()) {
                if (f == page.mainFrame()) continue;

                try {
                    ContextWrapper fw = new ContextWrapper();
                    fw.context = f;
                    String fName = f.name();
                    if (fName == null || fName.isEmpty()) fName = "anonymous";
                    fw.name = "Frame: " + fName + " (" + f.url() + ")";

                    result.wrappers.add(fw);
                    if (firstFrame == null) firstFrame = fw;

                    com.microsoft.playwright.ElementHandle element = f.frameElement();
                    double area = 0;
                    boolean isVisible = false;

                    if (element != null) {
                        com.microsoft.playwright.options.BoundingBox box = element.boundingBox();
                        if (box != null) {
                            area = box.width * box.height;
                            if (box.width > 0 && box.height > 0) {
                                isVisible = true;
                            }
                        }
                    }

                    System.out.println(" - Found Frame: " + fName + " | Area: " + area + " | Visible: " + isVisible);

                    if (isVisible && area > maxArea) {
                        maxArea = area;
                        result.best = fw;
                    }
                } catch (Exception e) {
                    System.out.println(" - Error checking frame " + f.name() + ": " + e.getMessage());
                }
            }

            if (result.best == mainPageWrapper && firstFrame != null) {
                System.out.println(" - No definitely visible frame found. Fallback to first found frame: " + firstFrame.name);
                result.best = firstFrame;
            }

            System.out.println("Scan complete. Best candidate: " + result.best.name);
            return result;
        }
    }

    private static ContextWrapper selectBestContext(Page rootPage, java.util.function.Consumer<String> uiLogger) {
        ScanResult res = scanContexts(rootPage);
        if (res != null && res.best != null) {
            if (uiLogger != null) uiLogger.accept("已自动选中最佳上下文: " + res.best.name);
            return res.best;
        }
        ContextWrapper fallback = new ContextWrapper();
        fallback.context = rootPage;
        fallback.name = "Main Page";
        if (uiLogger != null) uiLogger.accept("未能找到合适的上下文，回退使用主页面。");
        return fallback;
    }

    private static ContextWrapper reloadAndFindContext(Page rootPage, java.util.function.Consumer<String> uiLogger) {
        if (uiLogger != null) uiLogger.accept("正在刷新页面并重新识别最佳上下文...");
        synchronized (PLAYWRIGHT_LOCK) {
            try {
                rootPage.reload(new Page.ReloadOptions().setTimeout(15000));
            } catch (Exception reloadEx) {
                if (uiLogger != null) uiLogger.accept("Warning during reload: " + reloadEx.getMessage());
            }

            try {
                rootPage.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions().setTimeout(12000));
            } catch (Exception ignored) {}

            long deadline = System.currentTimeMillis() + 8000;
            int tries = 0;
            while (System.currentTimeMillis() < deadline) {
                ContextWrapper best = selectBestContext(rootPage, null);
                if (best != null && best.name != null && !"Main Page".equals(best.name)) {
                    if (uiLogger != null) uiLogger.accept("已识别到内容上下文: " + best.name);
                    return best;
                }
                tries++;
                if (uiLogger != null && tries % 4 == 0) uiLogger.accept("等待 iframe 就绪中...");
                try { rootPage.waitForTimeout(500); } catch (Exception ignored) {}
            }
            return selectBestContext(rootPage, uiLogger);
        }
    }

    private static String getPageContent(Object pageOrFrame) {
        if (pageOrFrame instanceof Page) {
            return ((Page) pageOrFrame).content();
        } else if (pageOrFrame instanceof com.microsoft.playwright.Frame) {
            return ((com.microsoft.playwright.Frame) pageOrFrame).content();
        }
        return "";
    }

    private static void saveDebugArtifacts(String rawHtml, String cleanedHtml, String code, java.util.function.Consumer<String> uiLogger) {
        try {
            java.nio.file.Path debugDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "autoweb", "debug");
            if (!java.nio.file.Files.exists(debugDir)) {
                java.nio.file.Files.createDirectories(debugDir);
            }
            
            if (rawHtml != null) {
                java.nio.file.Files.write(debugDir.resolve("debug_raw.html"), rawHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (cleanedHtml != null) {
                java.nio.file.Files.write(debugDir.resolve("debug_cleaned.html"), cleanedHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (code != null) {
                 java.nio.file.Path codePath = debugDir.resolve("debug_code.groovy");
                 java.nio.file.Files.write(codePath, code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                 uiLogger.accept("Debug code saved to: " + codePath.toAbsolutePath());
            }
            
            if (rawHtml != null || cleanedHtml != null) {
                uiLogger.accept("Debug HTMLs saved to: " + debugDir.toAbsolutePath());
            }
        } catch (Exception ex) {
            uiLogger.accept("Failed to save debug artifacts: " + ex.getMessage());
        }
    }

    private static void saveDebugCodeVariant(String code, String modelName, String tag, java.util.function.Consumer<String> uiLogger) {
        if (code == null) return;
        try {
            java.nio.file.Path debugDir = java.nio.file.Paths.get(System.getProperty("user.dir"), "autoweb", "debug");
            if (!java.nio.file.Files.exists(debugDir)) {
                java.nio.file.Files.createDirectories(debugDir);
            }
            String safeModel = modelName == null ? "UNKNOWN" : modelName.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
            if (safeModel.isEmpty()) safeModel = "UNKNOWN";
            String safeTag = tag == null ? "code" : tag.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
            if (safeTag.isEmpty()) safeTag = "code";
            String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            java.nio.file.Path codePath = debugDir.resolve("debug_code_" + safeModel + "_" + safeTag + "_" + ts + ".groovy");
            java.nio.file.Files.write(codePath, code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (uiLogger != null) {
                uiLogger.accept("Debug code saved to: " + codePath.toAbsolutePath());
            }
        } catch (Exception ex) {
            if (uiLogger != null) {
                uiLogger.accept("Failed to save debug code: " + ex.getMessage());
            }
        }
    }
    
    private static int clearDirFiles(java.nio.file.Path dir, java.util.function.Consumer<String> uiLogger) {
        if (dir == null) return 0;
        try {
            if (!java.nio.file.Files.exists(dir)) return 0;
            if (!java.nio.file.Files.isDirectory(dir)) return 0;
            java.util.List<java.nio.file.Path> files;
            try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.walk(dir)) {
                files = s.filter(p -> p != null && java.nio.file.Files.isRegularFile(p)).toList();
            }
            int deleted = 0;
            for (java.nio.file.Path p : files) {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                    deleted++;
                } catch (Exception ignored) {}
            }
            if (uiLogger != null) uiLogger.accept("已清理目录: " + dir.toAbsolutePath() + "，deleted=" + deleted);
            return deleted;
        } catch (Exception ex) {
            if (uiLogger != null) uiLogger.accept("清理目录失败: " + dir.toAbsolutePath() + "，err=" + ex.getMessage());
            return 0;
        }
    }

    private static void createGUI(Object initialContext, String initialCleanedHtml, String defaultPrompt, PlayWrightUtil.Connection connection) {
        // We need the root Page object to re-scan frames later.
        Page rootPage;
        if (initialContext instanceof com.microsoft.playwright.Frame) {
            rootPage = ((com.microsoft.playwright.Frame) initialContext).page();
        } else {
            rootPage = (Page) initialContext;
        }

        // State tracking for execution
        java.util.concurrent.atomic.AtomicBoolean hasExecuted = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.ConcurrentHashMap<String, ModelSession> sessionsByModel = new java.util.concurrent.ConcurrentHashMap<>();

        JFrame frame = new JFrame("AutoWeb 网页自动化控制台");
        AGENT_FRAME = frame;
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // --- Action Buttons (Declared early for layout) ---
        JButton btnPlan = new JButton("生成计划");
        JButton btnGetCode = new JButton("生成代码");
        JButton btnRefine = new JButton("修正代码");
        JButton btnExecute = new JButton("执行代码");

        java.util.function.Consumer<String> setStage = (stage) -> {};
        
        // --- Top Area: Settings + Prompt ---
        JPanel topContainer = new JPanel(new BorderLayout());

        // 1. Settings Area (Model + Buttons)
        JPanel settingsArea = new JPanel();
        settingsArea.setLayout(new BoxLayout(settingsArea, BoxLayout.Y_AXIS));
        settingsArea.setBorder(BorderFactory.createTitledBorder("控制面板"));

        JPanel selectionPanel = new JPanel();
        selectionPanel.setLayout(new BoxLayout(selectionPanel, BoxLayout.X_AXIS));

        JPanel modelPanel = new JPanel(new BorderLayout());
        JLabel lblModel = new JLabel("大模型(可多选):");
        String[] models = {"DeepSeek", "Qwen-Max", "Moonshot", "GLM", "Minimax", "Gemini", "Ollama Qwen3:8B"};
        JList<String> modelList = new JList<>(models);
        modelList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane modelScroll = new JScrollPane(modelList);
        modelScroll.setPreferredSize(new Dimension(160, 90));
        
        // Default selection
        String defaultModel = "DeepSeek";
        if ("QWEN_MAX".equals(ACTIVE_MODEL)) defaultModel = "Qwen-Max";
        else if ("GEMINI".equals(ACTIVE_MODEL)) defaultModel = "Gemini";
        else if ("MOONSHOT".equals(ACTIVE_MODEL)) defaultModel = "Moonshot";
        else if ("GLM".equals(ACTIVE_MODEL)) defaultModel = "GLM";
        else if ("MINIMAX".equals(ACTIVE_MODEL)) defaultModel = "Minimax";
        else if ("OLLAMA_QWEN3_8B".equals(ACTIVE_MODEL)) defaultModel = "Ollama Qwen3:8B";
        modelList.setSelectedValue(defaultModel, true);

        modelPanel.add(lblModel, BorderLayout.NORTH);
        modelPanel.add(modelScroll, BorderLayout.CENTER);

        Dimension actionButtonSize = new Dimension(120, 28);
        btnPlan.setPreferredSize(actionButtonSize);
        btnGetCode.setPreferredSize(actionButtonSize);
        btnRefine.setPreferredSize(actionButtonSize);
        btnExecute.setPreferredSize(actionButtonSize);
        btnPlan.setMaximumSize(actionButtonSize);
        btnGetCode.setMaximumSize(actionButtonSize);
        btnRefine.setMaximumSize(actionButtonSize);
        btnExecute.setMaximumSize(actionButtonSize);

        JPanel planCodePanel = new JPanel();
        planCodePanel.setLayout(new BoxLayout(planCodePanel, BoxLayout.Y_AXIS));
        btnPlan.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnGetCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        planCodePanel.add(btnPlan);
        planCodePanel.add(Box.createVerticalStrut(8));
        planCodePanel.add(btnGetCode);

        JPanel refineExecutePanel = new JPanel();
        refineExecutePanel.setLayout(new BoxLayout(refineExecutePanel, BoxLayout.Y_AXIS));
        btnRefine.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnExecute.setAlignmentX(Component.LEFT_ALIGNMENT);
        refineExecutePanel.add(btnRefine);
        refineExecutePanel.add(Box.createVerticalStrut(8));
        refineExecutePanel.add(btnExecute);

        JPanel reloadPanel = new JPanel();
        reloadPanel.setLayout(new BoxLayout(reloadPanel, BoxLayout.X_AXIS));
        JButton btnReloadPrompts = new JButton("重载提示规则");
        JButton btnUsageHelp = new JButton("查看使用说明");
        reloadPanel.add(btnReloadPrompts);
        reloadPanel.add(Box.createHorizontalStrut(8));
        reloadPanel.add(btnUsageHelp);
        
        JPanel reloadContainer = new JPanel();
        reloadContainer.setLayout(new BoxLayout(reloadContainer, BoxLayout.Y_AXIS));
        reloadContainer.add(reloadPanel);
        reloadContainer.add(Box.createVerticalStrut(6));
        JButton btnClearAll = new JButton("清空");
        btnClearAll.setAlignmentX(Component.LEFT_ALIGNMENT);
        reloadContainer.add(btnClearAll);

        selectionPanel.add(modelPanel);
        selectionPanel.add(Box.createHorizontalStrut(12));
        selectionPanel.add(planCodePanel);
        selectionPanel.add(Box.createHorizontalStrut(12));
        selectionPanel.add(refineExecutePanel);
        selectionPanel.add(Box.createHorizontalStrut(12));
        selectionPanel.add(reloadContainer);
        
        settingsArea.add(selectionPanel);
        
        topContainer.add(settingsArea, BorderLayout.NORTH);

        // 2. Prompt Panel
        JPanel promptPanel = new JPanel(new BorderLayout());
        promptPanel.setBorder(BorderFactory.createTitledBorder("用户任务"));
        JTextArea promptArea = new JTextArea(defaultPrompt);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setRows(3);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptPanel.add(promptScroll, BorderLayout.CENTER);

        JPanel refinePanel = new JPanel(new BorderLayout());
        refinePanel.setBorder(BorderFactory.createTitledBorder("补充说明提示信息"));
        JTextArea refineArea = new JTextArea();
        refineArea.setLineWrap(true);
        refineArea.setWrapStyleWord(true);
        refineArea.setRows(2);
        JScrollPane refineScroll = new JScrollPane(refineArea);
        refinePanel.add(refineScroll, BorderLayout.CENTER);

        JPanel promptContainer = new JPanel(new GridLayout(2, 1));
        promptContainer.add(promptPanel);
        promptContainer.add(refinePanel);
        
        topContainer.add(promptContainer, BorderLayout.CENTER);


        // --- Middle Area: Groovy Code (Tabs) ---
        JPanel codePanel = new JPanel(new BorderLayout());
        codePanel.setBorder(BorderFactory.createTitledBorder("模型回复内容"));
        JTabbedPane codeTabs = new JTabbedPane();
        codePanel.add(codeTabs, BorderLayout.CENTER);

        // --- Bottom Area: Output Log ---
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(BorderFactory.createTitledBorder("执行日志"));
        JTextArea outputArea = new JTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setEditable(false);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputPanel.add(outputScroll, BorderLayout.CENTER);


        // --- Split Panes ---
        // Code vs Output
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codePanel, outputPanel);
        bottomSplit.setResizeWeight(0.5);

        // Top (Settings+Prompt) vs Bottom (Code+Output)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topContainer, bottomSplit);
        mainSplit.setResizeWeight(0.15);
        
        frame.add(mainSplit, BorderLayout.CENTER);

        // --- Helper: UI Logger ---
        java.util.function.Consumer<String> uiLogger = (msg) -> {
             SwingUtilities.invokeLater(() -> {
                 outputArea.append(msg + "\n");
                 outputArea.setCaretPosition(outputArea.getDocument().getLength());
             });
             System.out.println(msg);
        };

        btnReloadPrompts.addActionListener(e -> {
            loadPrompts();
            JOptionPane.showMessageDialog(frame, "提示规则已重新载入！", "成功", JOptionPane.INFORMATION_MESSAGE);
        });
        
        btnUsageHelp.addActionListener(e -> {
            String text =
                    "使用流程：\n" +
                    "1) 在“用户任务”输入要做的事，然后可以选择一个或多个大模型，用于后续操作。\n" +
                    "2) 点“生成计划”：大模型会将用户任务分解为多个步骤的计划；每个步骤都需要知道访问哪个页面操作，因此若缺操作入口地址，会弹窗让你补充（支持多行、例如  订单管理页面: `http://xxxxxxx`）。\n" +
                    "3) 当计划生成完毕，点“生成代码”，我们将会获取大模型需要操作的页面数据，采集并压缩，然后发给大模型去生成可以执行任务的代码。\n" +
                    "4) 当代码生成完毕，点“执行代码”，执行脚本并在“执行日志”里输出过程，开始执行用户的任务。\n" +
                    "5) “修正代码”用于重新生成代码，主要是当代码执行出错，或者不达预期的时候，用户可以补充一些说明，让大模型去修改代码，然后后续执行修改后的代码，看是否符合预期（支持多次交互修正）。";
            JTextArea ta = new JTextArea(text, 12, 60);
            ta.setEditable(false);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setCaretPosition(0);
            JScrollPane sp = new JScrollPane(ta);
            JOptionPane.showMessageDialog(frame, sp, "使用说明", JOptionPane.INFORMATION_MESSAGE);
        });
        
        btnClearAll.addActionListener(e -> {
            boolean busy = !btnPlan.isEnabled() || !btnGetCode.isEnabled() || !btnRefine.isEnabled() || !btnExecute.isEnabled();
            if (busy) {
                int confirm = JOptionPane.showConfirmDialog(
                        frame,
                        "检测到当前可能有任务正在执行/生成中。\n清空将重置界面并清除缓存文件。\n\n是否仍要继续清空？",
                        "清空确认",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm != JOptionPane.YES_OPTION) return;
            }
            
            try { modelList.clearSelection(); } catch (Exception ignored) {}
            try { promptArea.setText(""); } catch (Exception ignored) {}
            try { refineArea.setText(""); } catch (Exception ignored) {}
            try { outputArea.setText(""); } catch (Exception ignored) {}
            try { codeTabs.removeAll(); } catch (Exception ignored) {}
            
            try {
                sessionsByModel.clear();
            } catch (Exception ignored) {}
            try { hasExecuted.set(false); } catch (Exception ignored) {}
            
            btnPlan.setEnabled(true);
            btnGetCode.setEnabled(true);
            btnRefine.setEnabled(true);
            btnExecute.setEnabled(true);
            setStage.accept("NONE");
            
            int deleted = 0;
            deleted += clearDirFiles(java.nio.file.Paths.get(System.getProperty("user.dir"), "autoweb", "cache"), uiLogger);
            deleted += clearDirFiles(java.nio.file.Paths.get(System.getProperty("user.dir"), "autoweb", "debug"), uiLogger);
            uiLogger.accept("清空完成：已重置界面，已删除缓存/调试文件数=" + deleted);
        });


        /**
         * 生成计划按钮逻辑 (btnPlan)
         * 这是一个入口函数，处理两种情况：
         * 1. 初始计划生成 (Step 1): 用户输入任务 -> 调用 LLM 生成初步计划。
         * 2. 计划修正/入口补充 (Step 2): 如果初步计划缺少 URL (STATUS: UNKNOWN)，弹窗让用户输入，然后调用 LLM 修正计划。
         */
        /**
         * 生成计划按钮逻辑 (btnPlan)
         * 负责计划生成的两个阶段：
         * 1. 初始生成 (PLAN_ENTRY): 首次点击，分析用户任务，生成包含 UNKNOWN 状态的初步计划，询问入口 URL。
         * 2. 计划修正 (PLAN_REFINE): 用户输入 URL 后再次点击（或通过弹窗触发），补充 URL 并将计划状态更新为 CONFIRMED。
         * 
         * 流程：
         * - 检查当前选中的模型状态。
         * - 若模型已有未确认计划且任务未变，提示用户是"补充入口"还是"重新生成"。
         * - "补充入口" -> 触发 PLAN_REFINE 流程 (输入框 -> 批量调用)。
         * - "重新生成" -> 触发 PLAN_ENTRY 流程 (清空会话 -> 批量调用)。
         */
        btnPlan.addActionListener(e -> {
            String currentPrompt = promptArea.getText();
            java.util.List<String> selectedModels = modelList.getSelectedValuesList();
            
            if (currentPrompt == null || currentPrompt.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请先在“用户任务”输入框中填写要执行的任务。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (selectedModels.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请至少选择一个大模型。", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // === 检查是否为 Step 2 (计划修正/补充入口) ===
            // 检查已选模型中，是否有"已生成计划但未确认(缺URL)"的情况
            java.util.List<String> pendingEntryModels = new java.util.ArrayList<>();
            for (String modelName : selectedModels) {
                ModelSession s = sessionsByModel.get(modelName);
                if (s == null) continue;
                if (s.userPrompt == null || !s.userPrompt.equals(currentPrompt)) continue;
                if (s.planText == null || s.planText.trim().isEmpty()) continue;
                if (s.planConfirmed) continue;
                pendingEntryModels.add(modelName);
            }
            if (!pendingEntryModels.isEmpty()) {
                // ... (弹窗逻辑)
                String msg = "检测到已有计划尚未确认入口地址。\n影响模型: " + String.join("，", pendingEntryModels) + "\n\n请选择：补充入口地址，或重新生成计划。";
                Object[] options = new Object[]{"补充入口地址", "重新生成计划", "取消"};
                int choice = JOptionPane.showOptionDialog(
                        frame,
                        msg,
                        "生成计划",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                );
                if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                    return;
                }
                if (choice == 0) {
                    for (String model : pendingEntryModels) {
                        if (codeTabs.indexOfTab(model) >= 0) continue;
                        ModelSession s = sessionsByModel.get(model);
                        JTextArea ta = new JTextArea(s == null || s.planText == null ? "" : s.planText);
                        ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
                        codeTabs.addTab(model, new JScrollPane(ta));
                    }

                    String entryInput = promptForMultilineInputBlocking(
                            frame,
                            "补充入口地址",
                            buildEntryInputHint(pendingEntryModels, sessionsByModel)
                    );
                    if (entryInput == null || entryInput.trim().isEmpty()) {
                        return;
                    }

                    setStage.accept("PLAN");
                    btnPlan.setEnabled(false);
                    btnGetCode.setEnabled(false);
                    btnRefine.setEnabled(false);
                    btnExecute.setEnabled(false);
                    outputArea.setText("");
                    uiLogger.accept("=== UI: 点击生成计划 | action=补充入口地址 | models=" + pendingEntryModels.size() + " ===");
                    uiLogger.accept("开始提交入口地址并修正规划...");
                    
                    java.util.List<String> refineModels = new java.util.ArrayList<>(pendingEntryModels);
                    new Thread(() -> {
                        try {
                            String currentUrlForRefine = safePageUrl(rootPage);
                            java.util.concurrent.ExecutorService ex2 = java.util.concurrent.Executors.newFixedThreadPool(refineModels.size());
                            java.util.List<java.util.concurrent.Future<?>> fs2 = new java.util.ArrayList<>();
                            for (String modelName : refineModels) {
                                fs2.add(ex2.submit(() -> {
                                    try {
                                        ModelSession session = sessionsByModel.computeIfAbsent(modelName, k -> new ModelSession());
                                        uiLogger.accept("阶段开始: model=" + modelName + ", action=PLAN_REFINE");
                                        uiLogger.accept("PLAN_REFINE Debug: model=" + modelName + ", entryInput='" + entryInput + "'");
                                        String payload = buildPlanRefinePayload(currentUrlForRefine, currentPrompt, entryInput);
                                        uiLogger.accept("PLAN_REFINE Payload Hash: " + payload.hashCode() + " | Length: " + payload.length());
                                        uiLogger.accept("阶段中: model=" + modelName + ", planMode=" + extractModeFromPayload(payload));
                                        String text = generateGroovyScript(currentPrompt, payload, uiLogger, modelName);
                                        String finalText = text == null ? "" : text;
                                        saveDebugCodeVariant(finalText, modelName, "plan_refine", uiLogger);
                                        PlanParseResult parsed = parsePlanFromText(finalText);
                                        if (!parsed.confirmed) {
                                            uiLogger.accept("PLAN_REFINE 未通过: model=" + modelName + " | Confirmed=false. LLM Output:\n" + finalText);
                                        }
                                        session.planText = parsed.planText;
                                        session.steps = parsed.steps;
                                        session.planConfirmed = parsed.confirmed;
                                        session.lastArtifactType = "PLAN";
                                        session.htmlPrepared = false;
                                        session.stepSnapshots.clear();
                                        SwingUtilities.invokeLater(() -> {
                                            int idx = codeTabs.indexOfTab(modelName);
                                            if (idx >= 0) {
                                                JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                                JTextArea ta = (JTextArea) sp.getViewport().getView();
                                                ta.setText(finalText);
                                            }
                                        });
                                        uiLogger.accept("阶段结束: model=" + modelName + ", action=PLAN_REFINE, confirmed=" + parsed.confirmed + ", steps=" + (parsed.steps == null ? 0 : parsed.steps.size()));
                                    } catch (Exception e2) {
                                        uiLogger.accept("PLAN_REFINE 失败: model=" + modelName + ", err=" + e2.getMessage());
                                    }
                                }));
                            }
                            for (java.util.concurrent.Future<?> f2 : fs2) {
                                try { f2.get(); } catch (Exception ignored2) {}
                            }
                            ex2.shutdown();
                        } finally {
                            setStage.accept("NONE");
                            SwingUtilities.invokeLater(() -> {
                                btnPlan.setEnabled(true);
                                btnGetCode.setEnabled(true);
                                btnRefine.setEnabled(true);
                                btnExecute.setEnabled(true);
                                uiLogger.accept("所有模型生成完成。");
                                if (isPlanReadyForModels(selectedModels, sessionsByModel, currentPrompt)) {
                                    showPlanReadyDialog(frame);
                                }
                            });
                        }
                    }).start();
                    return;
                }
            }

            // === Step 1: 初始计划生成逻辑 ===
            setStage.accept("PLAN");
            btnPlan.setEnabled(false);
            btnGetCode.setEnabled(false);
            btnRefine.setEnabled(false);
            btnExecute.setEnabled(false);
            outputArea.setText(""); // Clear output
            hasExecuted.set(false);
            codeTabs.removeAll(); // Clear existing tabs
            
            uiLogger.accept("=== UI: 点击生成计划 | models=" + selectedModels.size() + " ===");

            // Create tabs placeholder
            for (String model : selectedModels) {
                JTextArea ta = new JTextArea("// 正在等待 " + model + " 生成计划...\n// 请稍候...");
                ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
                codeTabs.addTab(model, new JScrollPane(ta));
            }

            new Thread(() -> {
                try {
                    uiLogger.accept("规划阶段：仅发送用户任务与提示规则，不采集 HTML。");
                    String currentUrlForPlan = safePageUrl(rootPage);
                    // 批量并发执行：为每个模型启动一个线程
                    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(selectedModels.size());
                    java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                    java.util.Set<String> needsEntryModels = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
                    
                    for (String modelName : selectedModels) {
                        futures.add(executor.submit(() -> {
                            try {
                                ModelSession session = sessionsByModel.computeIfAbsent(modelName, k -> new ModelSession());
                                uiLogger.accept("阶段开始: model=" + modelName + ", action=PLAN");
                                // ... (Session 初始化)
                                if (session.userPrompt == null || !session.userPrompt.equals(currentPrompt)) {
                                    session.userPrompt = currentPrompt;
                                    session.planText = null;
                                    session.steps = new java.util.ArrayList<>();
                                    session.stepSnapshots = new java.util.HashMap<>();
                                    session.planConfirmed = false;
                                    session.htmlPrepared = false;
                                    session.lastArtifactType = null;
                                }

                                String combinedForUrl = currentPrompt;
                                boolean hasUrl = extractFirstUrlFromText(combinedForUrl) != null || !extractUrlMappingsFromText(combinedForUrl).isEmpty();
                                
                                // 构建 Payload: 根据是否有 URL 决定是 PLAN_ONLY 还是 PLAN_ENTRY
                                String payload = hasUrl
                                        ? buildPlanOnlyPayload(currentUrlForPlan, combinedForUrl)
                                        : buildPlanEntryPayload(currentUrlForPlan, combinedForUrl);
                                uiLogger.accept("阶段中: model=" + modelName + ", planMode=" + extractModeFromPayload(payload));
                                
                                // 调用 LLM 生成计划
                                String planResult = generateGroovyScript(currentPrompt, payload, uiLogger, modelName);
                                String finalPlanResult = planResult == null ? "" : planResult;
                                saveDebugCodeVariant(finalPlanResult, modelName, "plan", uiLogger);
                                PlanParseResult parsed = parsePlanFromText(finalPlanResult);
                                session.planText = parsed.planText;
                                session.steps = parsed.steps;
                                session.planConfirmed = parsed.confirmed;
                                session.lastArtifactType = "PLAN";
                                session.htmlPrepared = false;
                                session.stepSnapshots.clear();

                                SwingUtilities.invokeLater(() -> {
                                    int idx = codeTabs.indexOfTab(modelName);
                                    if (idx >= 0) {
                                        JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                        JTextArea ta = (JTextArea) sp.getViewport().getView();
                                        ta.setText(finalPlanResult);
                                    }
                                });
                                if (parsed.steps == null || parsed.steps.isEmpty()) {
                                    uiLogger.accept("规划输出格式异常: " + modelName + " 未生成 Step 块，无法采集 HTML。请重新点“生成计划”或用“修正代码”让模型按要求输出 PLAN_START/Step/PLAN_END。");
                                }
                                if (parsed.hasQuestion || !parsed.confirmed) {
                                    needsEntryModels.add(modelName);
                                    uiLogger.accept("规划未完成: " + modelName + " 仍需要入口信息。将弹窗提示输入入口地址。");
                                } else {
                                    uiLogger.accept("规划已确认: " + modelName + "。点击“生成代码”将按计划采集 HTML 并生成脚本。");
                                }
                                uiLogger.accept("阶段结束: model=" + modelName + ", action=PLAN, confirmed=" + parsed.confirmed + ", steps=" + (parsed.steps == null ? 0 : parsed.steps.size()));
                            } catch (Exception genEx) {
                                try {
                                    uiLogger.accept("PLAN 失败: model=" + modelName + ", err=" + genEx.getMessage());
                                    saveDebugArtifact(newDebugTimestamp(), modelName, "PLAN", "exception", stackTraceToString(genEx), uiLogger);
                                } catch (Exception ignored) {}
                                SwingUtilities.invokeLater(() -> {
                                    int idx = codeTabs.indexOfTab(modelName);
                                    if (idx >= 0) {
                                        JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                        JTextArea ta = (JTextArea) sp.getViewport().getView();
                                        ta.setText("// 生成失败: " + genEx.getMessage());
                                    }
                                });
                            }
                        }));
                    }
                    
                    // Wait for all
                    for (java.util.concurrent.Future<?> f : futures) {
                        try { f.get(); } catch (Exception ignored) {}
                    }
                    executor.shutdown();

                    java.util.List<String> needList = new java.util.ArrayList<>(needsEntryModels);
                    needList.sort(String::compareTo);
                    if (needList.isEmpty()) {
                        setStage.accept("NONE");
                        SwingUtilities.invokeLater(() -> {
                            btnPlan.setEnabled(true);
                            btnGetCode.setEnabled(true);
                            btnRefine.setEnabled(true);
                            btnExecute.setEnabled(true);
                            uiLogger.accept("所有模型生成完成。");
                            if (isPlanReadyForModels(selectedModels, sessionsByModel, currentPrompt)) {
                                showPlanReadyDialog(frame);
                            }
                        });
                        return;
                    }

                    String entryInput = promptForMultilineInputBlocking(
                            frame,
                            "补充入口地址",
                            buildEntryInputHint(needList, sessionsByModel)
                    );
                    if (entryInput == null || entryInput.trim().isEmpty()) {
                        setStage.accept("NONE");
                        SwingUtilities.invokeLater(() -> {
                            btnPlan.setEnabled(true);
                            btnGetCode.setEnabled(true);
                            btnRefine.setEnabled(true);
                            btnExecute.setEnabled(true);
                            uiLogger.accept("已取消入口地址输入，规划未确认的模型仍需入口信息。");
                        });
                        return;
                    }

                    uiLogger.accept("开始提交入口地址并修正规划...");
                    String currentUrlForRefine = safePageUrl(rootPage);
                    java.util.List<String> refineModels = new java.util.ArrayList<>(needList);
                    new Thread(() -> {
                        try {
                            java.util.concurrent.ExecutorService ex2 = java.util.concurrent.Executors.newFixedThreadPool(refineModels.size());
                            java.util.List<java.util.concurrent.Future<?>> fs2 = new java.util.ArrayList<>();
                            for (String modelName : refineModels) {
                                fs2.add(ex2.submit(() -> {
                                    try {
                                        ModelSession session = sessionsByModel.computeIfAbsent(modelName, k -> new ModelSession());
                                        uiLogger.accept("阶段开始: model=" + modelName + ", action=PLAN_REFINE");
                                        uiLogger.accept("PLAN_REFINE Debug: model=" + modelName + ", entryInput='" + entryInput + "'");
                                        String payload = buildPlanRefinePayload(currentUrlForRefine, currentPrompt, entryInput);
                                        uiLogger.accept("PLAN_REFINE Payload Hash: " + payload.hashCode() + " | Length: " + payload.length());
                                        uiLogger.accept("阶段中: model=" + modelName + ", planMode=" + extractModeFromPayload(payload));
                                        String text = generateGroovyScript(currentPrompt, payload, uiLogger, modelName);
                                        String finalText = text == null ? "" : text;
                                        saveDebugCodeVariant(finalText, modelName, "plan_refine", uiLogger);
                                        PlanParseResult parsed = parsePlanFromText(finalText);
                                        if (!parsed.confirmed) {
                                            uiLogger.accept("PLAN_REFINE 未通过: model=" + modelName + " | Confirmed=false. LLM Output:\n" + finalText);
                                        }
                                        session.planText = parsed.planText;
                                        session.steps = parsed.steps;
                                        session.planConfirmed = parsed.confirmed;
                                        session.lastArtifactType = "PLAN";
                                        session.htmlPrepared = false;
                                        session.stepSnapshots.clear();
                                        SwingUtilities.invokeLater(() -> {
                                            int idx = codeTabs.indexOfTab(modelName);
                                            if (idx >= 0) {
                                                JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                                JTextArea ta = (JTextArea) sp.getViewport().getView();
                                                ta.setText(finalText);
                                            }
                                        });
                                        uiLogger.accept("阶段结束: model=" + modelName + ", action=PLAN_REFINE, confirmed=" + parsed.confirmed + ", steps=" + (parsed.steps == null ? 0 : parsed.steps.size()));
                                    } catch (Exception e2) {
                                        uiLogger.accept("PLAN_REFINE 失败: model=" + modelName + ", err=" + e2.getMessage());
                                    }
                                }));
                            }
                            for (java.util.concurrent.Future<?> f2 : fs2) {
                                try { f2.get(); } catch (Exception ignored2) {}
                            }
                            ex2.shutdown();
                        } finally {
                            setStage.accept("NONE");
                            SwingUtilities.invokeLater(() -> {
                                btnPlan.setEnabled(true);
                                btnGetCode.setEnabled(true);
                                btnRefine.setEnabled(true);
                                btnExecute.setEnabled(true);
                                uiLogger.accept("所有模型生成完成。");
                                if (isPlanReadyForModels(selectedModels, sessionsByModel, currentPrompt)) {
                                    showPlanReadyDialog(frame);
                                }
                            });
                        }
                    }).start();

                } catch (Exception ex) {
                    ex.printStackTrace();
                    setStage.accept("NONE");
                     SwingUtilities.invokeLater(() -> {
                        btnPlan.setEnabled(true);
                        btnGetCode.setEnabled(true);
                        btnRefine.setEnabled(true);
                        btnExecute.setEnabled(true);
                    });
                     uiLogger.accept("发生异常：" + ex.getMessage());
                }
            }).start();
        });

        /**
         * 生成代码按钮逻辑 (btnGetCode)
         * 1. 校验: 检查计划是否已确认(CONFIRMED)且包含步骤。
         * 2. HTML 采集 (Single Thread): 按计划步骤依次访问页面并采集 HTML (避免多浏览器冲突)。
         * 3. 代码生成 (Multi Thread): 组装 Payload (含 HTML) 并并发调用 LLM。
         */
        btnGetCode.addActionListener(e -> {
            String currentPrompt = promptArea.getText();
            java.util.List<String> selectedModels = modelList.getSelectedValuesList();

            if (currentPrompt == null || currentPrompt.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请先在“用户任务”输入框中填写要执行的任务。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (selectedModels.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请至少选择一个大模型。", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ... (校验逻辑: 检查 session.planConfirmed 等)
            java.util.List<String> readyModels = new java.util.ArrayList<>();
            java.util.List<String> notReadyModels = new java.util.ArrayList<>();
            for (String modelName : selectedModels) {
                ModelSession session = sessionsByModel.get(modelName);
                String reason = null;
                if (session == null || session.planText == null || session.planText.trim().isEmpty()) {
                    reason = "未生成计划";
                } else if (session.userPrompt == null || !session.userPrompt.equals(currentPrompt)) {
                    reason = "计划对应的用户任务已变化，请重新生成计划";
                } else if (!session.planConfirmed) {
                    if (session.steps == null || session.steps.isEmpty()) {
                        reason = "计划未确认且无步骤";
                    } else {
                        // 放宽校验：如果计划未确认，但有步骤且第一步有URL或当前页面可用，则允许尝试生成代码
                        PlanStep firstStep = session.steps.get(0);
                        String target = firstStep == null ? "" : firstStep.targetUrl;
                        boolean hasTarget = looksLikeUrl(target);
                        String currentUrl = safePageUrl(rootPage);
                        boolean hasLivePage = currentUrl != null && !currentUrl.isEmpty() && !"about:blank".equalsIgnoreCase(currentUrl);
                        
                        if (!hasTarget && !hasLivePage) {
                            reason = "计划未确认，且第一步无具体URL，当前浏览器也未打开网页";
                        }
                    }
                } else if (session.steps == null || session.steps.isEmpty()) {
                    reason = "计划缺少步骤（无法采集 HTML）";
                } else if (!"PLAN".equals(session.lastArtifactType)) {
                    reason = "请先生成计划";
                }

                if (reason == null) {
                    readyModels.add(modelName);
                } else {
                    notReadyModels.add(modelName + "（" + reason + "）");
                }
            }

            // Debug Log for parameters check
            if (uiLogger != null) {
                uiLogger.accept("Code Check: prompt='" + currentPrompt + "', ready=" + readyModels + ", notReady=" + notReadyModels);
            }

            StringBuilder tip = new StringBuilder();
            if (!readyModels.isEmpty()) {
                tip.append("可生成代码: ").append(String.join("，", readyModels)).append("\n");
            } else {
                tip.append("当前没有任何模型满足“可生成代码”的条件。\n");
            }
            if (!notReadyModels.isEmpty()) {
                tip.append("不可生成代码: ").append(String.join("，", notReadyModels)).append("\n");
            }
            JOptionPane.showMessageDialog(frame, tip.toString(), "生成代码检查", JOptionPane.INFORMATION_MESSAGE);

            if (readyModels.isEmpty()) {
                return;
            }

            setStage.accept("CODEGEN");
            btnPlan.setEnabled(false);
            btnGetCode.setEnabled(false);
            btnRefine.setEnabled(false);
            btnExecute.setEnabled(false);
            outputArea.setText("");
            hasExecuted.set(false);
            codeTabs.removeAll();

            uiLogger.accept("=== UI: 点击生成代码 | selectedModels=" + selectedModels.size() + ", readyModels=" + readyModels.size() + " ===");

            for (String model : selectedModels) {
                JTextArea ta = new JTextArea("// 正在等待 " + model + " 生成代码...\n// 请稍候...");
                ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
                codeTabs.addTab(model, new JScrollPane(ta));
            }

            new Thread(() -> {
                try {
                    int total = selectedModels.size();
                    int llmThreads = Math.max(1, Math.min(readyModels.size(), selectedModels.size()));
                    
                    // HTML 采集使用单线程 (htmlExecutor)，防止多模型同时操作浏览器导致状态混乱
                    java.util.concurrent.ExecutorService htmlExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
                    // LLM 调用使用多线程 (llmExecutor)，并发生成代码
                    java.util.concurrent.ExecutorService llmExecutor = java.util.concurrent.Executors.newFixedThreadPool(llmThreads);
                    java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();

                    try {
                        for (int i = 0; i < total; i++) {
                            String modelName = selectedModels.get(i);
                            int order = i + 1;

                            futures.add(llmExecutor.submit(() -> {
                                // ... (UI更新)
                                SwingUtilities.invokeLater(() -> {
                                    int idx = codeTabs.indexOfTab(modelName);
                                    if (idx >= 0) {
                                        JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                        JTextArea ta = (JTextArea) sp.getViewport().getView();
                                        ta.setText("// 排队号: " + order + "/" + total + "\n// 状态: 等待开始\n");
                                    }
                                });

                                ModelSession session = sessionsByModel.get(modelName);
                                // ... (校验)
                                if (!readyModels.contains(modelName)) {
                                    // ...
                                    return;
                                }

                                try {
                                    // 1. 采集 HTML (如果尚未采集)
                                    if (session.steps == null || session.steps.isEmpty()) {
                                        uiLogger.accept(modelName + ": 计划缺少步骤，无法采集 HTML。请重新生成计划。");
                                        return;
                                    }

                                    if (!session.htmlPrepared) {
                                        SwingUtilities.invokeLater(() -> {
                                            int idx = codeTabs.indexOfTab(modelName);
                                            if (idx >= 0) {
                                                JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                                JTextArea ta = (JTextArea) sp.getViewport().getView();
                                                ta.setText("// 排队号: " + order + "/" + total + "\n// 状态: 等待采集 HTML（单线程队列）\n");
                                            }
                                        });
                                        
                                        // 提交到单线程队列，阻塞等待采集完成
                                        java.util.concurrent.Future<?> htmlFuture = htmlExecutor.submit(() -> {
                                            if (session.htmlPrepared) return null;
                                            uiLogger.accept(modelName + ": 开始按计划采集 HTML（Step 数: " + session.steps.size() + "）...");
                                            java.util.List<HtmlSnapshot> snaps = prepareStepHtmls(rootPage, session.steps, uiLogger);
                                            java.util.Map<Integer, HtmlSnapshot> map = new java.util.HashMap<>();
                                            for (HtmlSnapshot s : snaps) map.put(s.stepIndex, s);
                                            session.stepSnapshots = map;
                                            session.htmlPrepared = true;
                                            uiLogger.accept(modelName + ": HTML 采集完成（snapshots=" + session.stepSnapshots.size() + "）");
                                            return null;
                                        });
                                        try { htmlFuture.get(); } catch (Exception ignored) {}
                                    }

                                    // 2. 生成代码
                                    SwingUtilities.invokeLater(() -> {
                                        int idx = codeTabs.indexOfTab(modelName);
                                        if (idx >= 0) {
                                            JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                            JTextArea ta = (JTextArea) sp.getViewport().getView();
                                            ta.setText("// 排队号: " + order + "/" + total + "\n// 状态: 正在调用模型生成代码...\n");
                                        }
                                    });

                                    java.util.List<HtmlSnapshot> snaps = new java.util.ArrayList<>(session.stepSnapshots.values());
                                    snaps.sort(java.util.Comparator.comparingInt(a -> a.stepIndex));
                                    
                                    // 构建 Payload (含 HTML) 并调用 LLM
                                    String payload = buildCodegenPayload(rootPage, session.planText, snaps);
                                    // ... (Debug log)
                                    uiLogger.accept("阶段中: model=" + modelName + ", action=CODEGEN, payloadMode=" + extractModeFromPayload(payload) + ", steps=" + session.steps.size() + ", snapshots=" + snaps.size());
                                    String generatedCode = generateGroovyScript(currentPrompt, payload, uiLogger, modelName);
                                    String normalizedCode = normalizeGeneratedGroovy(generatedCode);
                                    if (normalizedCode != null && !normalizedCode.equals(generatedCode)) {
                                        java.util.List<String> normalizeErrors = GroovyLinter.check(normalizedCode);
                                        boolean hasSyntaxIssue = normalizeErrors.stream().anyMatch(e2 -> e2.startsWith("Syntax Error") || e2.startsWith("Parse Error"));
                                        if (!hasSyntaxIssue) {
                                            generatedCode = normalizedCode;
                                        }
                                    }

                                    String finalCode = generatedCode == null ? "" : generatedCode;
                                    saveDebugCodeVariant(finalCode, modelName, "gen", uiLogger);
                                    session.lastArtifactType = "CODE";

                                    SwingUtilities.invokeLater(() -> {
                                        int idx = codeTabs.indexOfTab(modelName);
                                        if (idx >= 0) {
                                            JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                            JTextArea ta = (JTextArea) sp.getViewport().getView();
                                            ta.setText(finalCode);
                                        }
                                    });
                                } catch (Exception ex) {
                                    try {
                                        uiLogger.accept("CODEGEN 失败: model=" + modelName + ", err=" + ex.getMessage());
                                        saveDebugArtifact(newDebugTimestamp(), modelName, "CODEGEN", "exception", stackTraceToString(ex), uiLogger);
                                    } catch (Exception ignored) {}
                                    SwingUtilities.invokeLater(() -> {
                                        int idx = codeTabs.indexOfTab(modelName);
                                        if (idx >= 0) {
                                            JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(idx);
                                            JTextArea ta = (JTextArea) sp.getViewport().getView();
                                            ta.setText("// 生成失败: " + ex.getMessage());
                                        }
                                    });
                                }
                            }));
                        }

                        for (java.util.concurrent.Future<?> f : futures) {
                            try { f.get(); } catch (Exception ignored) {}
                        }
                    } finally {
                        try { llmExecutor.shutdown(); } catch (Exception ignored) {}
                        try { htmlExecutor.shutdown(); } catch (Exception ignored) {}
                    }

                    setStage.accept("NONE");
                    SwingUtilities.invokeLater(() -> {
                        btnPlan.setEnabled(true);
                        btnGetCode.setEnabled(true);
                        btnRefine.setEnabled(true);
                        btnExecute.setEnabled(true);
                        uiLogger.accept("所有模型生成完成。");
                    });
                } catch (Exception ex) {
                    setStage.accept("NONE");
                    SwingUtilities.invokeLater(() -> {
                        btnPlan.setEnabled(true);
                        btnGetCode.setEnabled(true);
                        btnRefine.setEnabled(true);
                        btnExecute.setEnabled(true);
                    });
                    uiLogger.accept("发生异常：" + ex.getMessage());
                }
            }).start();
        });


        // --- Logic: Refine Code ---
        btnRefine.addActionListener(e -> {
            int selectedIndex = codeTabs.getSelectedIndex();
            if (selectedIndex < 0) {
                JOptionPane.showMessageDialog(frame, "请先选择一个包含代码的标签页。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String modelName = codeTabs.getTitleAt(selectedIndex);
            JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(selectedIndex);
            JTextArea codeArea = (JTextArea) sp.getViewport().getView();
            String previousCode = codeArea.getText();

            String currentPrompt = promptArea.getText();
            String refineHint = refineArea.getText();
            String execOutput = outputArea.getText();

            if (previousCode == null || previousCode.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "当前没有可用于修正的代码。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (refineHint == null || refineHint.trim().isEmpty()) {
                int choice = JOptionPane.showConfirmDialog(
                        frame,
                        "未填写修正说明。是否直接提交修正？",
                        "提示",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE
                );
                if (choice != JOptionPane.YES_OPTION) {
                    refineArea.requestFocusInWindow();
                    return;
                }
            }

            btnGetCode.setEnabled(false);
            btnPlan.setEnabled(false);
            btnRefine.setEnabled(false);
            btnExecute.setEnabled(false);
            outputArea.setText(""); 
            uiLogger.accept("=== UI: 点击修正代码 | model=" + modelName + " ===");
            
            new Thread(() -> {
                try {
                    ModelSession session = sessionsByModel.computeIfAbsent(modelName, k -> new ModelSession());
                    if (session.userPrompt == null || session.userPrompt.trim().isEmpty()) {
                        session.userPrompt = currentPrompt;
                    }

                    boolean looksLikePlan = false;
                    try {
                        looksLikePlan = previousCode != null
                                && (previousCode.contains("PLAN_START") || previousCode.contains("PLAN_END"))
                                && !previousCode.contains("web.");
                    } catch (Exception ignored) {}
                    if (looksLikePlan) {
                        SwingUtilities.invokeLater(() -> {
                            btnPlan.setEnabled(true);
                            btnGetCode.setEnabled(true);
                            btnRefine.setEnabled(true);
                            btnExecute.setEnabled(true);
                            JOptionPane.showMessageDialog(frame, "当前标签页内容像是“计划”而不是“代码”。请先点击“生成代码”，或重新点击“生成计划”。", "提示", JOptionPane.INFORMATION_MESSAGE);
                        });
                        uiLogger.accept("已取消修正：检测到当前标签页为计划文本。");
                        return;
                    }

                    uiLogger.accept("阶段开始: model=" + modelName + ", action=REFINE_CODE");

                    ContextWrapper workingContext = reloadAndFindContext(rootPage, uiLogger);
                    String freshHtml = "";
                    try { freshHtml = getPageContent(workingContext.context); } catch (Exception ignored) {}
                    String freshCleanedHtml = HTMLCleaner.clean(freshHtml);
                    if (freshCleanedHtml.length() > 500000) {
                        freshCleanedHtml = freshCleanedHtml.substring(0, 500000) + "...(truncated)";
                    }
                    saveDebugArtifacts(freshHtml, freshCleanedHtml, null, uiLogger);

                    if (!session.planConfirmed) {
                        PlanParseResult parsed = parsePlanFromText(previousCode);
                        if (parsed.steps != null && !parsed.steps.isEmpty() && parsed.confirmed) {
                            session.planText = parsed.planText;
                            session.steps = parsed.steps;
                            session.planConfirmed = true;
                        }
                    }

                    if (session.planConfirmed && !session.htmlPrepared) {
                        java.util.List<HtmlSnapshot> snaps = prepareStepHtmls(rootPage, session.steps, uiLogger);
                        java.util.Map<Integer, HtmlSnapshot> map = new java.util.HashMap<>();
                        for (HtmlSnapshot s : snaps) map.put(s.stepIndex, s);
                        session.stepSnapshots = map;
                        session.htmlPrepared = true;
                    }

                    java.util.List<HtmlSnapshot> stepSnaps = new java.util.ArrayList<>(session.stepSnapshots.values());
                    stepSnaps.sort(java.util.Comparator.comparingInt(a -> a.stepIndex));
                        String payload = buildRefinePayload(rootPage, session.planText, stepSnaps, freshCleanedHtml, currentPrompt, refineHint);
                    uiLogger.accept("阶段中: model=" + modelName + ", action=REFINE_CODE, payloadMode=" + extractModeFromPayload(payload) + ", snapshots=" + stepSnaps.size());
                    String promptForRefine = currentPrompt;
                    try {
                        if (session.userPrompt != null && !session.userPrompt.equals(currentPrompt)) {
                            promptForRefine = "原用户任务:\n" + session.userPrompt + "\n\n当前用户任务:\n" + currentPrompt;
                        }
                    } catch (Exception ignored) {}
                    String refinedCode = generateRefinedGroovyScript(
                            promptForRefine, payload, previousCode, execOutput, refineHint, uiLogger, modelName
                    );

                    String normalizedRefined = normalizeGeneratedGroovy(refinedCode);
                    if (normalizedRefined != null && !normalizedRefined.equals(refinedCode)) {
                        java.util.List<String> normalizeErrors = GroovyLinter.check(normalizedRefined);
                        if (normalizeErrors.isEmpty()) {
                            refinedCode = normalizedRefined;
                        }
                    }
                    String finalRefinedCode = refinedCode == null ? "" : refinedCode;
                    saveDebugCodeVariant(finalRefinedCode, modelName, "refine", uiLogger);
                    session.lastArtifactType = "CODE";

                    SwingUtilities.invokeLater(() -> {
                        codeArea.setText(finalRefinedCode);
                        btnPlan.setEnabled(true);
                        btnGetCode.setEnabled(true);
                        btnRefine.setEnabled(true);
                        btnExecute.setEnabled(true);
                    });
                    setStage.accept(finalRefinedCode.trim().isEmpty() ? "NONE" : "READY_EXECUTE");
                    uiLogger.accept("Refine 完成。");
                    uiLogger.accept("阶段结束: model=" + modelName + ", action=REFINE_CODE, bytes(code)=" + utf8Bytes(finalRefinedCode));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        btnPlan.setEnabled(true);
                        btnGetCode.setEnabled(true);
                        btnRefine.setEnabled(true);
                        btnExecute.setEnabled(true);
                    });
                    setStage.accept("NONE");
                    uiLogger.accept("Refine 失败: " + ex.getMessage());
                }
            }).start();
        });


        // --- Logic: Execute Code ---
        btnExecute.addActionListener(e -> {
            int selectedIndex = codeTabs.getSelectedIndex();
            if (selectedIndex < 0) {
                JOptionPane.showMessageDialog(frame, "请先选择一个包含代码的标签页。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String modelName = codeTabs.getTitleAt(selectedIndex);
            JScrollPane sp = (JScrollPane) codeTabs.getComponentAt(selectedIndex);
            JTextArea codeArea = (JTextArea) sp.getViewport().getView();
            String code = codeArea.getText();

            if (code == null || code.trim().isEmpty()) {
                JOptionPane.showMessageDialog(frame, "当前没有可执行的代码。", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            if (code.contains("QUESTION:") && (!code.contains("web.click") && !code.contains("web.extract"))) {
                int confirm = JOptionPane.showConfirmDialog(frame, 
                    "检测到代码中包含 'QUESTION:' 且似乎没有具体执行逻辑。\n模型可能正在请求更多信息。\n\n是否仍要强制执行？", 
                    "执行确认", 
                    JOptionPane.YES_NO_OPTION, 
                    JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            btnGetCode.setEnabled(false);
            btnPlan.setEnabled(false);
            btnRefine.setEnabled(false);
            btnExecute.setEnabled(false);
            outputArea.setText(""); 
            uiLogger.accept("=== 开始执行代码 ===");
            setStage.accept("EXECUTING");
            
            new Thread(() -> {
                try {
                    String currentPrompt = promptArea.getText();
                    ModelSession session = sessionsByModel.get(modelName);
                    String entryUrl = chooseExecutionEntryUrl(session, currentPrompt);
                    uiLogger.accept("执行准备: model=" + modelName + ", entryUrl=" + (entryUrl == null ? "(null)" : entryUrl));

                    String beforeUrl = safePageUrl(rootPage);
                    boolean hasLivePage = !beforeUrl.isEmpty() && !"about:blank".equalsIgnoreCase(beforeUrl);
                    if (entryUrl == null || entryUrl.trim().isEmpty()) {
                        if (!hasLivePage) {
                            throw new RuntimeException("未找到入口URL，且当前浏览器没有可用页面。请在“用户任务”里包含入口链接（https://...），或先生成计划并补充入口地址。");
                        } else {
                            uiLogger.accept("执行前导航: 未提供入口URL，将使用当前页面 | current=" + beforeUrl);
                        }
                    }
                    ensureRootPageAtUrl(rootPage, entryUrl, uiLogger);

                    ContextWrapper bestContext = waitAndFindContext(rootPage, uiLogger);
                    Object executionTarget = bestContext == null ? rootPage : bestContext.context;
                    if (hasExecuted.get()) {
                         uiLogger.accept("检测到代码已执行过，正在重置页面状态...");
                         ContextWrapper freshContext = reloadAndFindContext(rootPage, uiLogger);
                         executionTarget = freshContext.context;
                    }
                    
                    executeWithGroovy(code, executionTarget, uiLogger);
                    hasExecuted.set(true);
                    
                    SwingUtilities.invokeLater(() -> {
                         btnPlan.setEnabled(true);
                         btnGetCode.setEnabled(true);
                         btnRefine.setEnabled(true);
                         btnExecute.setEnabled(true);
                    });
                    setStage.accept("READY_EXECUTE");
                    uiLogger.accept("=== 执行完成 ===");
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        btnPlan.setEnabled(true);
                        btnGetCode.setEnabled(true);
                        btnRefine.setEnabled(true);
                        btnExecute.setEnabled(true);
                    });
                    setStage.accept("READY_EXECUTE");
                    uiLogger.accept("=== 执行失败: " + ex.getMessage() + " ===");
                }
            }).start();
        });

        // Initialize frame size/location
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = 800;
        int height = screenSize.height - 50;
        frame.setSize(width, height);
        frame.setLocation(screenSize.width - width, 0);
        frame.setVisible(true);
    }

    private static void closeBrowserAndPages(PlayWrightUtil.Connection connection, java.util.function.Consumer<String> logger) {
        if (!BROWSER_CLOSED.compareAndSet(false, true)) return;
        try {
            if (logger != null) logger.accept("正在关闭页面与浏览器...");
        } catch (Exception ignored) {}
        try {
            if (connection != null && connection.browser != null) {
                try {
                    for (com.microsoft.playwright.BrowserContext ctx : connection.browser.contexts()) {
                        try {
                            for (Page p : ctx.pages()) {
                                try { p.close(); } catch (Exception ignored) {}
                            }
                        } catch (Exception ignored) {}
                        try { ctx.close(); } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        try {
            if (connection != null) {
                PlayWrightUtil.disconnectBrowser(connection.playwright, connection.browser);
            }
        } catch (Exception ignored) {}
        try {
            if (logger != null) logger.accept("浏览器已关闭。");
        } catch (Exception ignored) {}
    }

    private static String promptForMultilineInput(Component parent, String title, String message) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JTextArea ta = new JTextArea(6, 60);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        panel.add(new JLabel("<html>" + (message == null ? "" : message).replace("\n", "<br/>") + "</html>"), BorderLayout.NORTH);
        panel.add(new JScrollPane(ta), BorderLayout.CENTER);
        int result = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        String v = ta.getText();
        return v == null ? null : v.trim();
    }
    
    private static String promptForMultilineInputBlocking(Component parent, String title, String message) {
        if (SwingUtilities.isEventDispatchThread()) {
            return promptForMultilineInput(parent, title, message);
        }
        java.util.concurrent.atomic.AtomicReference<String> ref = new java.util.concurrent.atomic.AtomicReference<>(null);
        try {
            SwingUtilities.invokeAndWait(() -> ref.set(promptForMultilineInput(parent, title, message)));
        } catch (Exception ignored) {}
        return ref.get();
    }

    private static boolean isPlanReadyForModels(java.util.List<String> models, java.util.Map<String, ModelSession> sessionsByModel, String currentPrompt) {
        if (models == null || models.isEmpty()) return false;
        for (String modelName : models) {
            if (modelName == null || modelName.trim().isEmpty()) return false;
            ModelSession s = sessionsByModel == null ? null : sessionsByModel.get(modelName);
            if (s == null) return false;
            if (s.userPrompt == null || currentPrompt == null || !s.userPrompt.equals(currentPrompt)) return false;
            if (s.planText == null || s.planText.trim().isEmpty()) return false;
            if (!s.planConfirmed) return false;
            if (s.steps == null || s.steps.isEmpty()) return false;
            if (!"PLAN".equals(s.lastArtifactType)) return false;
        }
        return true;
    }

    private static void showPlanReadyDialog(JFrame frame) {
        if (frame == null) return;
        Runnable show = () -> {
            String msg = "计划已生成，可以点击“生成代码”。\n回车后关闭弹窗（也可以手动点击确认）。";
            JDialog dialog = new JDialog(frame, "提示", true);
            JPanel panel = new JPanel(new BorderLayout(12, 12));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JTextArea ta = new JTextArea(msg, 4, 40);
            ta.setEditable(false);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setOpaque(false);
            ta.setBorder(null);

            JButton ok = new JButton("确认");
            ok.addActionListener(ev -> dialog.dispose());
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            btnPanel.add(ok);

            panel.add(ta, BorderLayout.CENTER);
            panel.add(btnPanel, BorderLayout.SOUTH);
            dialog.setContentPane(panel);
            dialog.getRootPane().setDefaultButton(ok);
            dialog.pack();
            dialog.setLocationRelativeTo(frame);
            ok.requestFocusInWindow();
            dialog.setVisible(true);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    private static String callLLMWithTimeout(java.util.concurrent.Callable<String> task, long timeoutMillis, java.util.function.Consumer<String> uiLogger, String modelName) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<String> future = executor.submit(task);
        try {
            return future.get(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            future.cancel(true);
            if (uiLogger != null) {
                uiLogger.accept("LLM 调用超时，模型: " + modelName + "，已中止本次请求。");
            }
        } catch (Exception ex) {
            future.cancel(true);
            if (uiLogger != null) {
                uiLogger.accept("LLM 调用失败，模型: " + modelName + "，错误: " + ex.getMessage());
            }
        } finally {
            executor.shutdownNow();
        }
        return "";
    }
    
    private static String getModelKey(String displayName) {
        if (displayName == null) return "DEEPSEEK";
        switch (displayName) {
            case "Qwen-Max": return "QWEN_MAX";
            case "Gemini": return "GEMINI";
            case "Ollama Qwen3:8B": return "OLLAMA_QWEN3_8B";
            case "Minimax": return "MINIMAX";
            case "Moonshot": return "MOONSHOT";
            case "GLM": return "GLM";
            case "DeepSeek": return "DEEPSEEK";
            default: return "DEEPSEEK";
        }
    }

    private static String callModel(String modelName, String prompt, java.util.function.Consumer<String> uiLogger) {
        String modelKey = getModelKey(modelName);
        System.out.println("Calling LLM (model=" + modelKey + ")...");
        long t0 = System.currentTimeMillis();
        String code = "";
        
        switch (modelKey) {
            case "QWEN_MAX":
                code = LLMUtil.chatWithAliyun(prompt);
                break;
            case "MINIMAX":
                code = callLLMWithTimeout(() -> LLMUtil.chatWithMinimax(prompt), 180000L, uiLogger, "Minimax");
                if (code == null || code.trim().isEmpty()) {
                    if (uiLogger != null) uiLogger.accept("Minimax 调用未返回结果或发生错误。");
                }
                break;
            case "MOONSHOT":
                code = callLLMWithTimeout(() -> LLMUtil.chatWithMoonshot(prompt), 180000L, uiLogger, "Moonshot");
                if (code == null || code.trim().isEmpty()) {
                    if (uiLogger != null) uiLogger.accept("Moonshot 调用未返回结果或发生错误。");
                }
                break;
            case "GLM":
                code = callLLMWithTimeout(() -> LLMUtil.chatWithGLM(prompt), 180000L, uiLogger, "GLM");
                if (code == null || code.trim().isEmpty()) {
                    if (uiLogger != null) uiLogger.accept("GLM 调用未返回结果或发生错误。");
                }
                break;
            case "GEMINI":
                code = callLLMWithTimeout(() -> LLMUtil.chatWithGemini(prompt), 180000L, uiLogger, "Gemini");
                if (code == null || code.trim().isEmpty()) {
                    if (uiLogger != null) uiLogger.accept("Gemini 调用未返回结果或发生错误。");
                }
                break;
            case "OLLAMA_QWEN3_8B":
                code = LLMUtil.chatWithOllama(prompt, LLMUtil.OLLAMA_MODEL_QWEN3_8B, null, false);
                break;
            case "DEEPSEEK":
            default:
                code = LLMUtil.chatWithDeepSeek(prompt);
                break;
        }
        
        if (code != null) {
            code = code.replaceAll("```groovy", "").replaceAll("```java", "").replaceAll("```", "").trim();
        }
        long elapsed = System.currentTimeMillis() - t0;
        if (uiLogger != null) {
            uiLogger.accept(String.format("模型 %s 生成耗时: %.2f秒", modelName, elapsed / 1000.0));
        }
        return code;
    }

    /**
     * LLM 调用封装函数 (generateGroovyScript)
     * 1. 加载 Prompt 模板 (groovy_script_prompt.txt)
     * 2. 根据 Mode (PLAN/CODEGEN) 动态裁剪模板内容 (stripTaggedBlocks)
     * 3. 组装最终 Prompt (模板 + 用户任务 + Payload)
     * 4. 调用 LLM (callModel) 并记录 Debug Artifacts
     */
    private static String generateGroovyScript(String userPrompt, String cleanedHtml, java.util.function.Consumer<String> uiLogger, String modelName) {
        if (GROOVY_SCRIPT_PROMPT_TEMPLATE == null || GROOVY_SCRIPT_PROMPT_TEMPLATE.isEmpty()) {
            loadPrompts();
        }

        // 1. 确定当前模式 (PLAN_ONLY / PLAN_REFINE / PLAN_ENTRY / CODEGEN)
        String mode = extractModeFromPayload(cleanedHtml);
        String template = GROOVY_SCRIPT_PROMPT_TEMPLATE;
        
        // 2. 如果是生成计划阶段，移除模板中仅用于生成代码的部分 (减少 Token 消耗，避免干扰)
        if ("PLAN_ONLY".equalsIgnoreCase(mode) || "PLAN_REFINE".equalsIgnoreCase(mode) || "PLAN_ENTRY".equalsIgnoreCase(mode)) {
            template = stripTaggedBlocks(template, "[CODEGEN_ONLY_START]", "[CODEGEN_ONLY_END]");
        }
        
        // 3. 格式化 Prompt
        String prompt = String.format(template, userPrompt, cleanedHtml);

        // ... (Debug 记录逻辑)
        String ts = newDebugTimestamp();
        String debugMode = (mode == null || mode.trim().isEmpty()) ? "UNKNOWN" : mode.trim();
        logPayloadSummary(cleanedHtml, uiLogger);
        logRequestBytes("generateGroovyScript", modelName, debugMode, cleanedHtml, prompt, uiLogger);
        String payloadPath = saveDebugArtifact(ts, modelName, debugMode, "payload", cleanedHtml, uiLogger);
        String promptPath = saveDebugArtifact(ts, modelName, debugMode, "prompt", prompt, uiLogger);
        
        if (uiLogger != null) {
            uiLogger.accept("Prompt Context Length (Get Code): " + prompt.length() + " chars");
            if (payloadPath != null || promptPath != null) {
                uiLogger.accept("Debug Saved: ts=" + ts + ", payload=" + (payloadPath == null ? "" : payloadPath) + ", prompt=" + (promptPath == null ? "" : promptPath));
            }
        }

        // 4. 调用模型接口
        String result = callModel(modelName, prompt, uiLogger);
        
        // ... (Debug 保存响应)
        String responsePath = saveDebugArtifact(ts, modelName, debugMode, "response", result, uiLogger);
        if (uiLogger != null && responsePath != null) {
            uiLogger.accept("Debug Saved: ts=" + ts + ", response=" + responsePath);
            uiLogger.accept("请求完成: stage=generateGroovyScript, model=" + (modelName == null ? "" : modelName) + ", mode=" + debugMode + ", bytes(response)=" + utf8Bytes(result));
        }
        return result;
    }

    private static String extractModeFromPayload(String payload) {
        if (payload == null) return "";
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^\\s*MODE\\s*:\\s*([A-Z_]+)\\s*$");
            java.util.regex.Matcher m = p.matcher(payload);
            if (m.find()) {
                String v = m.group(1);
                return v == null ? "" : v.trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String stripTaggedBlocks(String src, String startTag, String endTag) {
        if (src == null) return "";
        if (startTag == null || endTag == null) return src;
        String out = src;
        int guard = 0;
        while (guard < 50) {
            int s = out.indexOf(startTag);
            if (s < 0) break;
            int e = out.indexOf(endTag, s + startTag.length());
            if (e < 0) {
                out = out.substring(0, s);
                break;
            }
            out = out.substring(0, s) + out.substring(e + endTag.length());
            guard++;
        }
        return out;
    }

    private static String generateRefinedGroovyScript(
        String originalUserPrompt,
        String cleanedHtml,
        String previousCode,
        String execOutput,
        String refineHint,
        java.util.function.Consumer<String> uiLogger,
        String modelName
    ) {
        if (REFINED_GROOVY_SCRIPT_PROMPT_TEMPLATE == null || REFINED_GROOVY_SCRIPT_PROMPT_TEMPLATE.isEmpty()) {
            loadPrompts();
        }

        String mode = extractModeFromPayload(cleanedHtml);
        String template = REFINED_GROOVY_SCRIPT_PROMPT_TEMPLATE;
        if ("PLAN_REFINE".equalsIgnoreCase(mode)) {
            template = stripTaggedBlocks(template, "[CODEGEN_ONLY_START]", "[CODEGEN_ONLY_END]");
        }

        String prompt = String.format(
            template,
            originalUserPrompt,
            cleanedHtml,
            previousCode,
            execOutput,
            refineHint
        );

        String ts = newDebugTimestamp();
        String debugMode = (mode == null || mode.trim().isEmpty()) ? "UNKNOWN" : mode.trim();
        logPayloadSummary(cleanedHtml, uiLogger);
        logRequestBytes("generateRefinedGroovyScript", modelName, debugMode, cleanedHtml, prompt, uiLogger);
        String payloadPath = saveDebugArtifact(ts, modelName, debugMode, "payload", cleanedHtml, uiLogger);
        String promptPath = saveDebugArtifact(ts, modelName, debugMode, "prompt", prompt, uiLogger);
        
        if (uiLogger != null) {
            uiLogger.accept("Prompt Context Length (Refine Code): " + prompt.length() + " chars");
            if (payloadPath != null || promptPath != null) {
                uiLogger.accept("Debug Saved: ts=" + ts + ", payload=" + (payloadPath == null ? "" : payloadPath) + ", prompt=" + (promptPath == null ? "" : promptPath));
            }
        }

        String result = callModel(modelName, prompt, uiLogger);
        String responsePath = saveDebugArtifact(ts, modelName, debugMode, "response", result, uiLogger);
        if (uiLogger != null && responsePath != null) {
            uiLogger.accept("Debug Saved: ts=" + ts + ", response=" + responsePath);
            uiLogger.accept("请求完成: stage=generateRefinedGroovyScript, model=" + (modelName == null ? "" : modelName) + ", mode=" + debugMode + ", bytes(response)=" + utf8Bytes(result));
        }
        return result;
    }
    
    private static String normalizeGeneratedGroovy(String code) {
        if (code == null) return null;
        String normalized = code;
        normalized = normalized.replaceAll("(?m)^(\\s*)(PLAN:|THINK:|ANALYSIS:|REASONING:|思考过程|计划|PLAN_START|PLAN_END|QUESTION:)\\b", "$1// $2");
        normalized = normalized.replaceAll("(?m)^(\\s*)(-\\s*[Pp]lan\\b.*)", "$1// $2");
        normalized = normalized.replaceAll("(?m)^(\\s*)(\\*\\s*[Pp]lan\\b.*)", "$1// $2");
        normalized = normalized.replaceAll("(?m)^(\\s*)(\\[Plan\\].*)", "$1// $2");
        normalized = normalized.replaceAll("(?m)^(\\s*)(<plan>.*)</plan>\\s*$", "$1// $2");
        normalized = normalized.replaceAll("(?m)^(\\s*)(<think>.*)</think>\\s*$", "$1// $2");
        normalized = normalized.replaceAll("(?m)^(\\s*)(思考:.*)", "$1// $2");

        boolean applyNormalization =
                normalized.contains("web.extractList(") ||
                normalized.matches("(?s).*\\browCount\\b\\s*=\\s*web\\.count\\(.*") ||
                normalized.contains("rowTexts") ||
                normalized.contains("joinedRow");
        if (applyNormalization) {
            String replacement = "def rows = web.extractFirstPageRows(containerSelector, rowSelector, cellSelector)\n" +
                    "rows.each { row -> web.log(row) }\n";
            java.util.regex.Pattern blockPatternA = java.util.regex.Pattern.compile(
                    "(?s)def\\s+rowCount\\s*=\\s*web\\.count\\([^\\n]*\\).*?allRowsOutput\\.each\\s*\\{.*?\\}\\s*"
            );
            java.util.regex.Pattern blockPatternB = java.util.regex.Pattern.compile(
                    "(?s)def\\s+rowCount\\s*=\\s*web\\.count\\([^\\n]*\\).*?(?=def\\s+totalCountText|def\\s+totalText|web\\.getText\\()"
            );
            normalized = blockPatternA.matcher(normalized).replaceAll(replacement);
            normalized = blockPatternB.matcher(normalized).replaceAll(replacement);
            normalized = normalized.replaceAll("(?s)def\\s+rowTexts\\s*=\\s*\\[\\].*?def\\s+joinedRow\\s*=.*?web\\.log\\(joinedRow\\).*?(?=def\\s+totalCountText|def\\s+totalText|web\\.getText\\()", "");
            normalized = normalized.replaceAll("(?s)int\\s+rowCount\\s*=\\s*web\\.count\\([^\\n]*\\).*?for\\s*\\(\\s*int\\s+i\\s*=\\s*0;.*?\\)\\s*\\{.*?web\\.log\\(joinedRow\\)\\s*;?\\s*\\}.*?(?=def\\s+totalCountText|def\\s+totalText|web\\.getText\\()", replacement);
            
            java.util.regex.Pattern getTextLogAssignPattern = java.util.regex.Pattern.compile("(?s)(?:String|def|var)?\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*web\\.getText\\((\"|')(.*?)\\2\\)\\s*\\n\\s*web\\.log\\(\\1\\)");
            java.util.regex.Matcher getTextLogAssignMatcher = getTextLogAssignPattern.matcher(normalized);
            StringBuffer getTextLogAssignBuffer = new StringBuffer();
            while (getTextLogAssignMatcher.find()) {
                String varName = getTextLogAssignMatcher.group(1);
                String sel = getTextLogAssignMatcher.group(3);
                String replacementBlock = "def " + varName + " = web.getText(\"" + sel.replace("\"", "\\\"") + "\")\nweb.log(" + varName + ")";
                getTextLogAssignMatcher.appendReplacement(getTextLogAssignBuffer, java.util.regex.Matcher.quoteReplacement(replacementBlock));
            }
            getTextLogAssignMatcher.appendTail(getTextLogAssignBuffer);
            normalized = getTextLogAssignBuffer.toString();
            
            java.util.regex.Pattern getTextLogPattern = java.util.regex.Pattern.compile("(?s)web\\.getText\\((\"|')(.*?)\\1\\)\\s*\\n\\s*web\\.log\\(([^\\)]+)\\)");
            java.util.regex.Matcher getTextLogMatcher = getTextLogPattern.matcher(normalized);
            StringBuffer getTextLogBuffer = new StringBuffer();
            while (getTextLogMatcher.find()) {
                String sel = getTextLogMatcher.group(2);
                String varName = getTextLogMatcher.group(3).trim();
                String replacementBlock = "def " + varName + " = web.getText(\"" + sel.replace("\"", "\\\"") + "\")\nweb.log(" + varName + ")";
                getTextLogMatcher.appendReplacement(getTextLogBuffer, java.util.regex.Matcher.quoteReplacement(replacementBlock));
            }
            getTextLogMatcher.appendTail(getTextLogBuffer);
            normalized = getTextLogBuffer.toString();
        }
        normalized = escapeNonInterpolatedDollarInDoubleQuotedStrings(normalized);
        return normalized;
    }

    private static String escapeNonInterpolatedDollarInDoubleQuotedStrings(String code) {
        if (code == null || code.indexOf('$') < 0 || code.indexOf('"') < 0) return code;
        StringBuilder out = new StringBuilder(code.length() + 16);
        int n = code.length();
        int i = 0;
        boolean inDouble = false;
        int doubleQuoteLen = 0;
        while (i < n) {
            char c = code.charAt(i);
            if (!inDouble) {
                if (c == '"') {
                    if (i + 2 < n && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                        inDouble = true;
                        doubleQuoteLen = 3;
                        out.append("\"\"\"");
                        i += 3;
                        continue;
                    }
                    inDouble = true;
                    doubleQuoteLen = 1;
                    out.append('"');
                    i++;
                    continue;
                }
                out.append(c);
                i++;
                continue;
            }

            if (c == '\\') {
                out.append(c);
                if (i + 1 < n) {
                    out.append(code.charAt(i + 1));
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            if (doubleQuoteLen == 3) {
                if (c == '"' && i + 2 < n && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                    inDouble = false;
                    doubleQuoteLen = 0;
                    out.append("\"\"\"");
                    i += 3;
                    continue;
                }
            } else {
                if (c == '"') {
                    inDouble = false;
                    doubleQuoteLen = 0;
                    out.append('"');
                    i++;
                    continue;
                }
            }

            if (c == '$') {
                char next = (i + 1) < n ? code.charAt(i + 1) : '\0';
                boolean interpolation = next == '{' || next == '_' || Character.isLetter(next);
                if (!interpolation) out.append('\\');
                out.append('$');
                i++;
                continue;
            }

            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static void executeWithGroovy(String scriptCode, Object pageOrFrame, java.util.function.Consumer<String> logger) throws Exception {
        // 1. Static Linting
        java.util.List<String> lintErrors = GroovyLinter.check(scriptCode);
        if (!lintErrors.isEmpty()) {
            StringBuilder sb = new StringBuilder("Static Analysis Found Issues:\n");
            for (String err : lintErrors) {
                sb.append("- ").append(err).append("\n");
            }
            logger.accept(sb.toString());
            
            // Abort on security violations
            boolean hasSecurityError = lintErrors.stream().anyMatch(e -> e.startsWith("Security Error"));
            boolean hasSyntaxError = lintErrors.stream().anyMatch(e -> e.startsWith("Syntax Error") || e.startsWith("Parse Error"));
            if (hasSecurityError || hasSyntaxError) {
                 throw new RuntimeException("Execution aborted due to static analysis violations.");
            }
        }

        try {
            groovy.lang.Binding binding = new groovy.lang.Binding();
            binding.setVariable("page", pageOrFrame);
            
            // Inject WebDSL
            WebDSL dsl = new WebDSL(pageOrFrame, logger);
            binding.setVariable("web", dsl);
            
            // Redirect print output to our UI logger
            binding.setVariable("out", new java.io.PrintWriter(new java.io.Writer() {
                private StringBuilder buffer = new StringBuilder();
                @Override
                public void write(char[] cbuf, int off, int len) {
                    buffer.append(cbuf, off, len);
                    checkBuffer();
                }
                @Override
                public void flush() { checkBuffer(); }
                @Override
                public void close() { flush(); }
                
                private void checkBuffer() {
                    int newline = buffer.indexOf("\n");
                    while (newline != -1) {
                        String line = buffer.substring(0, newline);
                        logger.accept(line); // Log to UI
                        buffer.delete(0, newline + 1);
                        newline = buffer.indexOf("\n");
                    }
                }
            }, true)); // Auto-flush

            groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell(binding);
            shell.evaluate(scriptCode);
            logger.accept("Groovy script executed successfully.");
        } catch (Exception e) {
            logger.accept("Groovy execution failed: " + e.getMessage());
            // 抛出异常以便主程序捕获并退出
            throw e;
        }
    }

}
