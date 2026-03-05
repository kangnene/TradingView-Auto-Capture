import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TradingViewAutoCapture {
    public static void main(String[] args) {
        // 입력받은 심볼이 없으면 나스닥 기본값 설정
        String inputSymbols = (args.length > 0) ? args[0] : "NASDAQ:NDX";
        String[] symbols = inputSymbols.split(",");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            // auth.json으로 로그인 세션 유지
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setDeviceScaleFactor(1.5)  // 1.5배 고해상도
                .setStorageStatePath(Paths.get("auth.json")));

            // 날짜 폴더 생성
            String today = LocalDate.now().toString();
            String baseDir = "screenshots/" + today;
            Files.createDirectories(Paths.get(baseDir));

            // 병렬 처리: 최대 5개 스레드로 동시에 캡처 (필요시 조절)
            ExecutorService executor = Executors.newFixedThreadPool(5);

            for (String symbol : symbols) {
                executor.submit(() -> {
                    String targetSymbol = symbol.trim().toUpperCase(); // TSLA -> TSLA
                    String savePath = baseDir + "/" + targetSymbol.replace(":", "_") + ".png";

                    System.out.println(targetSymbol + " 트레이딩뷰 접속 중...");
                    try {
                        Page page = context.newPage();
                        // 기존 로직 그대로 유지
                        page.navigate("https://www.tradingview.com/chart/?symbol=" + targetSymbol + "&interval=1",
                                new Page.NavigateOptions().setTimeout(120000));

                        page.waitForSelector(".chart-container",
                                new Page.WaitForSelectorOptions().setTimeout(20000));

                        // 팝업 제거
                        page.addStyleTag(new Page.AddStyleTagOptions()
                                .setContent(".tv-dialog__close, .js-dialog__close, div[class*='overlap-manager'], [class*='dialog'], [class*='overlay'] { display: none !important; }"));
                        page.keyboard().press("Escape");
                        page.waitForTimeout(1000);

                        try {
                            // 1. "1분" 버튼 클릭 시도
                            Locator btn1m = page.locator("button[data-value='1'], [data-name='1']").first();
                            if (btn1m.isVisible()) {
                                btn1m.click(new Locator.ClickOptions().setForce(true));
                            } else {
                                page.locator("span:has-text('1m'), div:has-text('1m')").last()
                                        .click(new Locator.ClickOptions().setForce(true));
                            }
                        } catch (Exception e) {
                            // 2. 실패하면 키보드 단축키(Control+Down)로 범위 조정
                            for (int i = 0; i < 10; i++) {
                                page.keyboard().press("Control+ArrowDown");
                                page.waitForTimeout(200);
                            }
                        }

                        page.waitForTimeout(5000);
                        page.mouse().move(0, 0);

                        // 스크린샷 저장
                        page.screenshot(new Page.ScreenshotOptions()
                            .setPath(Paths.get(savePath))
                            .setType(ScreenshotType.PNG)
                            .setFullPage(false));  // 필요시 false 유지

                        System.out.println("캡쳐 완료: " + savePath);
                        page.close();
                    } catch (Exception e) {
                        System.out.println(targetSymbol + " 처리 중 오류 발생");
                        e.printStackTrace();
                        // 오류 시 별도 로그 저장
                        try {
                            String errorPath = baseDir + "/" + targetSymbol.replace(":", "_") + "_error.log";
                            Files.write(Paths.get(errorPath), e.toString().getBytes());
                        } catch (Exception ex) {
                            System.out.println("오류 로그 저장 실패: " + ex.getMessage());
                        }
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.MINUTES); // 최대 30분 대기
            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
