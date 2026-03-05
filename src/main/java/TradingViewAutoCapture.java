import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDate;

public class TradingViewAutoCapture {
    public static void main(String[] args) {
        // 입력받은 심볼이 없으면 나스닥 기본값 설정
        String inputSymbols = (args.length > 0) ? args[0] : "NASDAQ:NDX";
        String[] symbols = inputSymbols.split(",");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            // auth.json으로 로그인 세션 유지
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setStorageStatePath(Paths.get("auth.json")));
            Page page = context.newPage();

            // 1. 날짜 폴더 생성 (예: screenshots/2026-03-05)
            String today = LocalDate.now().toString();
            String baseDir = "screenshots/" + today;
            Files.createDirectories(Paths.get(baseDir));

            for (String symbol : symbols) {
                String targetSymbol = symbol.trim();
                String savePath = baseDir + "/" + targetSymbol.replace(":", "_") + ".jpg";

                System.out.println(targetSymbol + " 트레이딩뷰 접속 중...");
                try {
                    // 사용자님 기존 로직: URL 접속 (타임아웃 120초)
                    page.navigate("https://www.tradingview.com/chart/?symbol=" + targetSymbol + "&interval=1",
                            new Page.NavigateOptions().setTimeout(120000));

                    page.waitForSelector(".chart-container", 
                            new Page.WaitForSelectorOptions().setTimeout(20000));

                    // 사용자님 기존 로직: 팝업 제거 (CSS 주입 + ESC)
                    page.addStyleTag(new Page.AddStyleTagOptions()
                            .setContent(".tv-dialog__close, .js-dialog__close, div[class*='overlap-manager'], [class*='dialog'], [class*='overlay'] { display: none !important; }"));
                    page.keyboard().press("Escape");
                    page.waitForTimeout(1000);

                    // 사용자님 기존 로직: '1일' 범위(1D) 클릭 시도 (3단계 방어막)
                    try {
                        Locator btn1D = page.locator("button[data-value='1D'], [data-name='1D']").first();
                        if (btn1D.isVisible()) {
                            btn1D.click(new Locator.ClickOptions().setForce(true));
                        } else {
                            page.locator("span:has-text('1D'), div:has-text('1D')").last().click(new Locator.ClickOptions().setForce(true));
                        }
                    } catch (Exception e) {
                        // 클릭 실패 시 강제 스크롤 방어막
                        for (int i = 0; i < 10; i++) {
                            page.keyboard().press("Control+ArrowDown");
                            page.waitForTimeout(200);
                        }
                    }

                    page.waitForTimeout(5000); // 로딩 대기
                    page.mouse().move(0, 0);   // 마우스 치우기

                    // 최종 캡쳐 및 저장 (날짜 폴더 안으로)
                    page.screenshot(new Page.ScreenshotOptions()
                            .setPath(Paths.get(savePath))
                            .setType(ScreenshotType.JPEG)
                            .setQuality(100));

                    System.out.println("캡쳐 완료: " + savePath);

                } catch (Exception e) {
                    System.out.println(targetSymbol + " 처리 중 오류 발생");
                    e.printStackTrace();
                }
            }
            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
