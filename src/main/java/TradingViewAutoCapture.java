import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public class TradingViewAutoCapture {
    public static void main(String[] args) {
        // 입력받은 심볼이 없으면 나스닥 기본값
        String inputSymbols = (args.length > 0) ? args[0] : "NASDAQ:NDX";
        String[] symbols = inputSymbols.split(",");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setDeviceScaleFactor(1.0)
                    .setViewportSize(2560, 1440)
                    .setStorageStatePath(Paths.get("auth.json"))
            );

            // ========================== 폴더 구조 수정 ==========================
            Path screenshotsRoot = Paths.get("screenshots");
            Files.createDirectories(screenshotsRoot);

            String today = LocalDate.now().toString();
            Path todayFolder = screenshotsRoot.resolve(today);
            Files.createDirectories(todayFolder);
            // ===================================================================

            // 순차 실행: symbols 배열을 반복
            for (String symbol : symbols) {
                final String currentSymbol = symbol.trim().toUpperCase();
                Path screenshotPath = todayFolder.resolve(currentSymbol.replace(":", "_") + ".png");

                System.out.println("=== Capturing: " + currentSymbol + " ===");
                try {
                    Page page = context.newPage();

                    System.out.println(currentSymbol + " 페이지 접속 중...");
                    page.navigate(
                        "https://www.tradingview.com/chart/?symbol=" + currentSymbol + "&interval=1",
                        new Page.NavigateOptions().setTimeout(180000)
                    );

                    System.out.println(currentSymbol + " 차트 로딩 대기...");
                    page.waitForSelector(".chart-container canvas", new Page.WaitForSelectorOptions().setTimeout(20000));

                    System.out.println(currentSymbol + " 팝업 제거 중...");
                    page.addStyleTag(new Page.AddStyleTagOptions()
                        .setContent(".tv-dialog__close, .js-dialog__close, div[class*='overlap-manager'], [class*='dialog'], [class*='overlay'] { display: none !important; }"));
                    page.keyboard().press("Escape");

                    page.waitForTimeout(1000);
                    page.keyboard().press("Alt+R");
                    
                    page.waitForTimeout(2000);

                    System.out.println(currentSymbol + " 1D 버튼 클릭 시도...");
                    try {
                        Locator btn1D = page.locator("button[data-value='1D'], [data-name='1D']").first();
                        if (btn1D.isVisible()) {
                            btn1D.click(new Locator.ClickOptions().setForce(true));
                        } else {
                            page.locator("span:has-text('1D'), div:has-text('1D')").last()
                                .click(new Locator.ClickOptions().setForce(true));
                        }
                    } catch (Exception e) {
                        System.out.println(currentSymbol + " 1D 버튼 클릭 실패, 키보드 단축키 시도...");
                        for (int i = 0; i < 10; i++) {
                            page.keyboard().press("Control+ArrowDown");
                            page.waitForTimeout(200);
                        }
                    }

                    page.waitForTimeout(3000);
                    page.mouse().move(0, 0);

                    System.out.println(currentSymbol + " 스크린샷 저장 중...");
                    if (Files.exists(screenshotPath)) {
                        Files.delete(screenshotPath);
                    }
                    page.screenshot(new Page.ScreenshotOptions()
                        .setPath(screenshotPath)
                        .setType(ScreenshotType.PNG)
                        .setFullPage(false)
                    );

                    System.out.println(currentSymbol + " 캡처 완료 ✅ " + screenshotPath.toString());
                    page.close();

                } catch (Exception e) {
                    System.out.println(currentSymbol + " 처리 중 오류 ❌");
                    e.printStackTrace();

                    try {
                        Path errorPath = todayFolder.resolve(currentSymbol.replace(":", "_") + "_error.log");
                        Files.write(errorPath, e.toString().getBytes());
                    } catch (Exception ex) {
                        System.out.println(currentSymbol + " 오류 로그 저장 실패: " + ex.getMessage());
                    }
                }
            }

            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
