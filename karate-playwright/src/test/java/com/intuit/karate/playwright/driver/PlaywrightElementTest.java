package com.intuit.karate.playwright.driver;

import com.intuit.karate.driver.Key;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PlaywrightElementTest {

    private Locator locator;
    private PlaywrightDriver driver;
    private PlaywrightElement element;

    @BeforeEach
    void beforeEach() {
        Locator rootLocator = Mockito.mock(Locator.class);
        locator = Mockito.mock(Locator.class);
        when(rootLocator.first()).thenReturn(locator);
        driver = Mockito.mock(PlaywrightDriver.class);
        when(driver.rootLocator("root")).thenReturn(rootLocator);
        element = new PlaywrightElement(driver, new PlaywrightToken(driver, "root", null));
    }

    @Test
    void textEnter() {
        element.input("Karate" + Key.INSTANCE.ENTER);
        Mockito.verify(locator).pressSequentially(eq("Karate"), argThat(matchesDelay(0)));
        Mockito.verify(locator).press(Mockito.eq("Enter"), any());
    }

    @Test
    void shiftTextEnter() {
        element.input(Key.INSTANCE.SHIFT + "karate" + Key.INSTANCE.ENTER);
        Mockito.verify(locator).press(eq("Shift+k"), any());
        Mockito.verify(locator).pressSequentially(eq("arate"), argThat(matchesDelay(0)));
        Mockito.verify(locator).press(eq("Enter"), any());
    }

    @Test
    void ctrlShiftText() {
        element.input(new StringBuilder().append(Key.INSTANCE.CONTROL).append(Key.INSTANCE.SHIFT).append("karate").toString());
        Mockito.verify(locator).press(eq("Control+Shift+k"), any());
        Mockito.verify(locator).pressSequentially(eq("arate"), argThat(matchesDelay(0)));
    }

    @Test
    void withDelay() {
        element.input(new String[]{"Input", "Karate"}, 10);
        Mockito.verify(locator).pressSequentially(eq("Input"), argThat(matchesDelay(10)));
        Mockito.verify(locator).pressSequentially(eq("Karate"), argThat(matchesDelay(10)));

    }

    private ArgumentMatcher<Locator.PressSequentiallyOptions> matchesDelay(int delay) {
        return options -> delay == options.delay.intValue();
    }

}
