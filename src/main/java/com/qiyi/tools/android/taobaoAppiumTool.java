package com.qiyi.tools.android;

import com.alibaba.fastjson2.JSONObject;
import com.qiyi.android.AndroidDeviceManager;
import com.qiyi.android.BaseMobileRPAProcessor;
import com.qiyi.tools.Tool;
import com.qiyi.util.DingTalkUtil;

import com.qiyi.util.LLMUtil;
import io.github.pigmesh.ai.deepseek.core.chat.UserMessage;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.nativekey.AndroidKey;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.nativekey.KeyEvent;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;


import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class taobaoAppiumTool extends BaseMobileRPAProcessor implements Tool {
    
    private static Logger logger = LogManager.getLogger(taobaoAppiumTool.class);

    public static void main(String[] aStrings)
    {
        taobaoAppiumTool tool = new taobaoAppiumTool();

        JSONObject parmas = new JSONObject();
        parmas.put("product_name", "皮蛋瘦肉粥");
        parmas.put("product_type", "外卖商品");
        parmas.put("target_shop_name", "三米粥铺(杭州留下店)");

        String result  = tool.execute(parmas, null, null);

        System.out.println(result);
    }
    
    public taobaoAppiumTool() {
        this.setAppPackage("com.taobao.taobao");
        this.setAppActivity("com.taobao.tao.welcome.Welcome");
    }

    @Override
    public String getName() {
        return "search_taobao_product";
    }

    @Override
    public String getDescription() {
        return "用于在淘宝App中搜索商品或查找外卖店铺。当用户指令涉及'点外卖'、'找附近的店'、'买药'、'搜商品'、'查店铺'等场景时使用。参数：product_name (String, 必填) - 搜索关键词，可以是商品名（如'奶茶'、'感冒药'）或店铺名（如'肯德基'）；product_type (String, 选填) - '普通商品'或'外卖商品'，当意图包含'附近'、'外卖'、'送药'、'小时达'或查询餐饮/药品店铺时，请务必设置为 '外卖商品'；target_shop_name (String, 选填) - 目标店铺名称，如果指定了此参数，工具将在搜索结果中查找匹配的店铺并点击进入。";
    }
    
    @Override
    public void initDriver(String udid, String appiumServerUrl)
            throws MalformedURLException {

        HashMap<String, Object> uiAutomator2Options = new HashMap<>();

        // Override to add specific options for Taobao
        UiAutomator2Options options = new UiAutomator2Options()
                .setUdid(udid)
                .setNoReset(true)
                .setLocaleScript("zh-Hans-CN") 
                .setNewCommandTimeout(Duration.ofMinutes(3))
                .setAdbExecTimeout(Duration.ofSeconds(120))
                .setAppPackage(appPackage)
                .setAppActivity(appActivity);

        options.setCapability("uiautomator2Options", uiAutomator2Options);
        
        driver = new AndroidDriver(
                new URL(appiumServerUrl), options);
        driver.setSetting("allowInvisibleElements", true);
        // driver.activateApp(appPackage); // Welcome activity handles startup
    }

    @Override
    public String execute(JSONObject params, String senderId, List<String> atUserIds) {
        String serial = null;
        List<String> users = new ArrayList<>();
        if (senderId != null) users.add(senderId);

        try {
            serial = getDeviceSerial(params);
            
            // 1. Prepare Product Name and Type
            String productName = null;
            String productType = "普通商品";
            String targetShopName = null;
            if (params != null) {
                productName = params.getString("product_name");
                if (params.containsKey("product_type")) {
                    productType = params.getString("product_type");
                }
                if (params.containsKey("target_shop_name")) {
                    targetShopName = params.getString("target_shop_name");
                }
            }

            if (productName == null || productName.trim().isEmpty()) {
                return reportError(users, "未指定商品名称 (product_name)。请在指令中明确指定要搜索的商品。");
            }

            DingTalkUtil.sendTextMessageToEmployees(users, "正在初始化 Appium Driver (目标设备: " + serial + ")...");

            // 2. Init Driver
            try {
                this.initDriver(serial, "http://127.0.0.1:4723");
                // Ensure the app is activated and in foreground
                ((AndroidDriver) driver).activateApp(appPackage);
            } catch (Exception e) {
                 e.printStackTrace();
                 return reportError(users, "Appium 连接或启动 App 失败: " + e.getMessage());
            }

            DingTalkUtil.sendTextMessageToEmployees(users, "Appium 连接成功，正在搜索: " + productName);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            // 3. Handle Product Type logic
            if ("外卖商品".equals(productType)) {
                try {
                    System.out.println("Looking for (Type: " + productType + ")...");

                    findElementAndWaitToClick("//android.widget.TextView[@content-desc=\"闪购,未选中\"]", 5);

                    System.out.println("Clicked '闪购'");
                } catch (Exception e) {
                    System.out.println("Exception: " + e.getMessage());
                    System.out.println("Warning: '闪购' not found, proceeding to search directly...");
                }

                // 4. Search Flow (Handle existing input state -> Input -> Enter)
                WebElement inputField = null;
                try {
                    
                    String searchEntranceXpath = "//android.widget.FrameLayout[@resource-id=\"com.taobao.taobao:id/search_view\"]//android.view.View[@content-desc=\"搜索栏\"]";
                    findElementAndWaitToClick(searchEntranceXpath, 5);

                    inputField = findElementAndWaitToClick("//android.webkit.WebView[@text=\"闪购搜索\"]//android.widget.Button", 5);

                    if (inputField != null) {
                        System.out.println("Found Input Field, sending text: " + productName);
                        
                        // Use Clipboard + Paste to handle Chinese input
                        try {
                             ((AndroidDriver) driver).setClipboardText(productName);
                             Thread.sleep(500);
                             ((AndroidDriver) driver).pressKey(new KeyEvent(AndroidKey.PASTE));
                             System.out.println("Pasted text from clipboard");
                        } catch (Exception e) {
                             System.out.println("Clipboard paste failed, falling back to Actions: " + e.getMessage());
                             new Actions(driver).sendKeys(productName).perform();
                        }


                        // Press Enter to search
                        System.out.println("Pressing Enter key...");
                        driver.pressKey(new KeyEvent(AndroidKey.ENTER));
                        
                        // Hide keyboard
                        try {
                            ((AndroidDriver) driver).hideKeyboard();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Search flow error: " + e.getMessage());
                    if (inputField == null) {
                        return reportError(users, "未找到搜索框 (EditText)");
                    }
                }

                StringBuilder finalResult = new StringBuilder();

                // 5. Scroll to load more results (3 times)
                for (int i = 0; i < 3; i++) {
                     try {
                         this.scroll(0.5, Direction.UP, 0.8);
                         Thread.sleep(1000);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                }

                // 6. Find elements
                List<WebElement> webElements = this.findElementsAndWait("//android.view.View[contains(@resource-id,\"shopItemCard\")]/android.view.View[1]", 5);

                StringBuilder sb = new StringBuilder();

                for (WebElement element : webElements) {
                    String elementText = element.getText();
                    sb.append("店铺信息:").append("\n");
                    sb.append(elementText).append("\n");
                    System.out.println(elementText); 

                    // Check if this is the target shop
                    if (targetShopName != null && !targetShopName.isEmpty() && elementText.contains(targetShopName)) {
                        System.out.println("Found target shop: " + targetShopName + ". Entering...");
                        try {
                            // Try to click the element directly first (as we found it)
                            // We need to click the parent "shopItemCard" not the text view
                            // Using UiAutomator to find the parent is safer
                            // Regex updated to ".*shopItemCard.*" to match IDs with dynamic suffixes
                            String uiSelector = "new UiSelector().resourceIdMatches(\".*shopItemCard.*\").childSelector(" +
                                    "new UiSelector().textContains(\"" + targetShopName + "\"))";
                            WebElement targetCard = driver.findElement(AppiumBy.androidUIAutomator(uiSelector));
                            targetCard.click();
                        } catch (Exception e) {
                            System.err.println("Direct click failed: " + e.getMessage());
                            try {
                                // Fallback: Scroll and find
                                String scrollSelector = "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(" +
                                        "new UiSelector().resourceIdMatches(\".*shopItemCard.*\").childSelector(" +
                                        "new UiSelector().textContains(\"" + targetShopName + "\")))";
                                WebElement targetCard = driver.findElement(AppiumBy.androidUIAutomator(scrollSelector));
                                targetCard.click();
                            } catch (Exception e2) {
                                System.err.println("Failed to click shop card: " + e2.getMessage());
                                DingTalkUtil.sendTextMessageToEmployees(users, "找到店铺 " + targetShopName + " 但点击失败：" + e2.getMessage());
                                continue;
                            }
                        }

                        try {
                            DingTalkUtil.sendTextMessageToEmployees(users, "找到并已进入店铺：" + targetShopName + "。");
                            
                            // Placeholder for subsequent logic (reading new page content, searching products inside shop)
                            // TODO: Implement reading new page content and searching for products inside the shop
                            
                            return "已进入店铺：" + targetShopName;
                        } catch (Exception e) {
                             e.printStackTrace();
                        }
                    }
                }
                
                if (targetShopName != null && !targetShopName.isEmpty()) {
                     String msg = "未找到目标店铺：" + targetShopName;
                     DingTalkUtil.sendTextMessageToEmployees(users, msg);
                     return msg;
                }

                finalResult.append(sb.toString());

                // 7. Process with DeepSeek LLM
                String rawData = finalResult.toString();
                if (rawData.length() > 0) {
                    try {
                        String prompt = "将以下文本（每块代表一个店铺信息）整理提取出：店铺名称，配送费，起送费，月售，距离，配送时间。\n" +
                                        "每一行一条记录，格式为：店铺名称：XXX，配送费：XXX，起送费：XXX，月售：XXX，距离：XXX，配送时间：XXX\n" +
                                        "只返回整理后的数据，除了店铺名称其他信息带上单位，不要其他废话。\n" +
                                        "文本内容如下：\n" + rawData;
                        
                        List<io.github.pigmesh.ai.deepseek.core.chat.Message> messages = new ArrayList<>();
                        messages.add(UserMessage.builder().addText(prompt).build());
                        String structuredData = LLMUtil.chatWithDeepSeek(messages, false);
                        DingTalkUtil.sendTextMessageToEmployees(users, structuredData);
                        return structuredData;
                    } catch (Exception e) {
                         System.out.println("LLM Processing Failed: " + e.getMessage());
                         DingTalkUtil.sendTextMessageToEmployees(users, rawData);
                         return rawData; // Fallback
                    }
                }

                DingTalkUtil.sendTextMessageToEmployees(users, finalResult.toString());
                return finalResult.toString();
            } 

            return "";

        } catch (Throwable e) {
            e.printStackTrace();
            return reportError(users, "Appium 执行异常 (Throwable): " + e.getMessage());
        } finally {
            this.cleanup();
        }
    }

    private void cleanup() {
        if (driver != null) {
            try {
                // Force stop the app to ensure clean state for next run
                ((AndroidDriver) driver).terminateApp(appPackage);
            } catch (Exception e) {
                // Ignore if fails
            }
        }
        this.quitDriver();
    }

    private String getDeviceSerial(JSONObject params) throws Exception {
        String serial = null;
        
        if (params != null)
            serial = params.getString("serial");
        
        if (serial != null && !serial.isEmpty()) {
            return serial;
        }
        
        // If no serial provided, get the first connected device
        List<String> devices = AndroidDeviceManager.getInstance().getDevices();
        if (devices.isEmpty()) {
            throw new Exception("No Android devices connected.");
        }
        return devices.get(0);
    }

    private String reportError(List<String> users, String msg) {
        System.err.println(msg);
        try {
            DingTalkUtil.sendTextMessageToEmployees(users, "Error: " + msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return msg;
    }
}
