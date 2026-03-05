import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TradingViewAutoCapture {
    public static void main(String[] args) {
        // 입력받은 심볼이 없으면 나스닥 기본값
        String inputSymbols = (args.length > 0) ? args[0] : "NASDAQ:NDX";
        String[] symbols = inputSymbols.split(",");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            // auth.json으로 로그인 세션 유지, 화면 확대 조정
            BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setDeviceScaleFactor(1.0)   // 화면 과도 확대 제거
                    .setViewportSize(1920, 1080)
                    .setStorageStatePath(Paths.get("auth.json"))
            );

            // screenshots 루트 폴더 생성
            Path screenshotsRoot = Paths.get("screenshots");
            Files.createDirectories(screenshotsRoot);

            // 날짜 폴더 생성 (screenshots/YYYY-MM-DD)
            String today = LocalDate.now().toString();
            Path todayFolder = screenshotsRoot.resolve(today);
            Files.createDirectories(todayFolder);

            // 병렬 처리: 최대 8개 스레드
            ExecutorService executor = Executors.newFixedThreadPool(8);

            for (String symbol : symbols) {
                executor.submit(() -> {
                    String targetSymbol = symbol.trim().toUpperCase();
                    Path screenshotPath = todayFolder.resolve(targetSymbol.replace(":", "_") + ".png");

                    System.out.println(targetSymbol + " 트레이딩뷰 접속 중...");
                    try {
                        Page page = context.newPage();

                        // 트레이딩뷰 1분봉 하루치 차트 접속
                        page.navigate(
                            "https://www.tradingview.com/chart/?symbol=" + targetSymbol + "&interval=1",
                            new Page.NavigateOptions().setTimeout(120000)
                        );

                        page.waitForSelector(".chart-container canvas",
                            new Page.WaitForSelectorOptions().setTimeout(20000));

                        // 팝업 제거
                        page.addStyleTag(new Page.AddStyleTagOptions()
                            .setContent(".tv-dialog__close, .js-dialog__close, div[class*='overlap-manager'], [class*='dialog'], [class*='overlay'] { display: none !important; }"));
                        page.keyboard().press("Escape");
                        page.waitForTimeout(1000);

                        // '1일' 범위(1D) 클릭 시도
                        try {
                            Locator btn1D = page.locator("button[data-value='1D'], [data-name='1D']").first();
                            if (btn1D.isVisible()) {
                                btn1D.click(new Locator.ClickOptions().setForce(true));
                            } else {
                                page.locator("span:has-text('1D'), div:has-text('1D')").last()
                                    .click(new Locator.ClickOptions().setForce(true));
                            }
                        } catch (Exception e) {
                            // 키보드 단축키(Control+Down)로 범위 조정
                            for (int i = 0; i < 10; i++) {
                                page.keyboard().press("Control+ArrowDown");
                                page.waitForTimeout(200);
                            }
                        }

                        page.waitForTimeout(5000);
                        page.mouse().move(0, 0);

                        // 기존 파일 삭제 후 스크린샷 저장 (덮어쓰기)
                        if (Files.exists(screenshotPath)) {
                            Files.delete(screenshotPath);
                        }

                        page.screenshot(new Page.ScreenshotOptions()
                            .setPath(screenshotPath)
                            .setType(ScreenshotType.PNG)
                            .setFullPage(false)
                        );

                        System.out.println("캡쳐 완료: " + screenshotPath.toString());
                        page.close();

                    } catch (Exception e) {
                        System.out.println(targetSymbol + " 처리 중 오류 발생");
                        e.printStackTrace();

                        // 오류 로그 저장
                        try {
                            Path errorPath = todayFolder.resolve(targetSymbol.replace(":", "_") + "_error.log");
                            Files.write(errorPath, e.toString().getBytes());
                        } catch (Exception ex) {
                            System.out.println("오류 로그 저장 실패: " + ex.getMessage());
                        }
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.MINUTES);
            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
