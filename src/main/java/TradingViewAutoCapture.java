import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public class TradingViewAutoCapture {
    public static void main(String[] args) {

        String inputSymbols = (args.length > 0) ? args[0] : "NASDAQ:NDX";
        String startDate = (args.length > 1) ? args[1] : LocalDate.now().toString();
        String endDate = (args.length > 2) ? args[2] : LocalDate.now().toString();

        String startDateTime = startDate + " 00:15";
        String endDateTime = endDate + " 23:45";

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

            Path screenshotsRoot = Paths.get("screenshots");
            Files.createDirectories(screenshotsRoot);

            String today = LocalDate.now().toString();
            Path todayFolder = screenshotsRoot.resolve(today);
            Files.createDirectories(todayFolder);

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

                    page.waitForSelector(
                        ".chart-container canvas",
                        new Page.WaitForSelectorOptions().setTimeout(20000)
                    );

                    System.out.println(currentSymbol + " 팝업 제거 중...");

                    page.addStyleTag(
                        new Page.AddStyleTagOptions().setContent(
                            ".tv-dialog__close, .js-dialog__close, div[class*='overlap-manager'], [class*='dialog'], [class*='overlay'] { display: none !important; }"
                        )
                    );

                    page.keyboard().press("Escape");

                    page.waitForTimeout(1000);

                    System.out.println("Range 입력창 열기 (Alt+R)");

                    page.keyboard().press("Alt+R");

                    page.waitForTimeout(2000);

                    System.out.println("Start date 입력: " + startDateTime);

                    page.keyboard().type(startDateTime);

                    page.keyboard().press("Tab");

                    System.out.println("End date 입력: " + endDateTime);

                    page.keyboard().type(endDateTime);

                    page.keyboard().press("Enter");

                    page.waitForTimeout(4000);

                    page.mouse().move(0, 0);

                    System.out.println(currentSymbol + " 스크린샷 저장 중...");

                    if (Files.exists(screenshotPath)) {
                        Files.delete(screenshotPath);
                    }

                    page.screenshot(
                        new Page.ScreenshotOptions()
                            .setPath(screenshotPath)
                            .setType(ScreenshotType.PNG)
                            .setFullPage(false)
                    );

                    System.out.println(
                        currentSymbol + " 캡처 완료 ✅ " + screenshotPath.toString()
                    );

                    page.close();

                } catch (Exception e) {

                    System.out.println(currentSymbol + " 처리 중 오류 ❌");

                    e.printStackTrace();

                    try {

                        Path errorPath =
                            todayFolder.resolve(
                                currentSymbol.replace(":", "_") + "_error.log"
                            );

                        Files.write(errorPath, e.toString().getBytes());

                    } catch (Exception ex) {

                        System.out.println(
                            currentSymbol + " 오류 로그 저장 실패: " + ex.getMessage()
                        );

                    }

                }

            }

            browser.close();

        } catch (Exception e) {

            e.printStackTrace();

        }

    }

}
