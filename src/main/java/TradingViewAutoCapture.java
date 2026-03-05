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
        // 입력받은 심볼이 없으면 나스닥 기본값 설정
        String inputSymbols = (args.length > 0) ? args[0] : "NASDAQ:NDX";
        String[] symbols = inputSymbols.split(",");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            // auth.json으로 로그인 세션 유지, 화면 확대 조정
            BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setDeviceScaleFactor(1.0)  // 화면 과도 확대 제거
                    .setViewportSize(1920, 1080)
                    .setStorageStatePath(Paths.get("auth.json"))
            );

            // 날짜 폴더 생성 (screenshots/YYYY-MM-DD/)
            String today = LocalDate.now().toString();
            String baseDir = "screenshots/" + today;
            Files.createDirectories(Paths.get(baseDir));

            // 병렬 처리: 최대 5개 스레드
            ExecutorService executor = Executors.newFixedThreadPool(8);

            for (String symbol : symbols) {
                executor.submit(() -> {
                    String targetSymbol = symbol.trim().toUpperCase();
                    String savePath = baseDir + "/" + targetSymbol.replace(":", "_") + ".png";
                    Path screenshotPath = Paths.get(savePath);

                    System.out.println(targetSymbol + " 트레이딩뷰 접속 중...");
                    try {
                        Page page = context.newPage();

                        // 트레이딩뷰 1분봉 하루치 차트 접속
                        page.navigate(
                            "https://www.tradingview.com/chart/?symbol=" + targetSymbol + "&interval=1",
                            new Page.NavigateOptions().setTimeout(10000)
                        );

                        page.waitForSelector(".chart-container canvas",
                            new Page.WaitForSelectorOptions().setTimeout(10000));

                        // 팝업 제거
                        page.addStyleTag(new Page.AddStyleTagOptions()
                            .setContent(".tv-dialog__close, .js-dialog__close, div[class*='overlap-manager'], [class*='dialog'], [class*='overlay'] { display: none !important; }"));
                        page.keyboard().press("Escape");
                        page.waitForTimeout(1000);

                        // '1일' 범위(1D) 클릭 시도 (사용자님의 3단계 방어막 유지)
                        try {
                            // 1. "1D"라는 속성값을 가진 버튼을 찾습니다.
                            Locator btn1D = page.locator("button[data-value='1D'], [data-name='1D']").first();
                            
                            if (btn1D.isVisible()) {
                                // 2. 버튼이 보이면 클릭합니다.
                                btn1D.click(new Locator.ClickOptions().setForce(true));
                            } else {
                                // 3. 버튼이 안 보이면 "1D"라는 텍스트가 써진 요소를 찾아 클릭합니다.
                                page.locator("span:has-text('1D'), div:has-text('1D')").last().click(new Locator.ClickOptions().setForce(true));
                            }
                        } catch (Exception e) {
                            // 4. 위 방법이 다 실패하면 키보드 단축키(Control+Down)로 차트 범위를 조절합니다.
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

                        System.out.println("캡쳐 완료: " + savePath);
                        page.close();

                    } catch (Exception e) {
                        System.out.println(targetSymbol + " 처리 중 오류 발생");
                        e.printStackTrace();

                        // 오류 로그 저장
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
