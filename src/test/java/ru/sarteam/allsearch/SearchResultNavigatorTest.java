package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class SearchResultNavigatorTest {

    @Test
    void movesBetweenHitsBeforeChangingFile() {
        SearchResultNavigator navigator = navigatorWithHits(3);
        navigator.selectIndex(0);

        SearchResultNavigator.Navigation next = navigator.next(0, 2);

        assertEquals(1, next.hitIndex());
        assertNull(next.fileDirection());
    }

    @Test
    void movesToNextFileAfterLastHit() {
        SearchResultNavigator navigator = navigatorWithHits(2);
        navigator.selectIndex(1);

        SearchResultNavigator.Navigation next = navigator.next(0, 2);

        assertEquals(1, next.fileDirection());
        assertNull(next.hitIndex());
    }

    @Test
    void wrapsInsideSingleFile() {
        SearchResultNavigator navigator = navigatorWithHits(2);
        navigator.selectIndex(0);

        SearchResultNavigator.Navigation previous = navigator.previous(0, 1);

        assertEquals(1, previous.hitIndex());
        assertNull(previous.fileDirection());
    }

    @Test
    void selectsFirstHitAfterMovingForwardToFile() {
        SearchResultNavigator navigator = navigatorWithHits(3);
        navigator.setPendingHitForFileDirection(1);

        assertEquals(0, navigator.initialHitIndex());
        navigator.clearPendingHit();
        assertEquals(0, navigator.initialHitIndex());
    }

    @Test
    void selectsLastHitAfterMovingBackwardToFile() {
        SearchResultNavigator navigator = navigatorWithHits(3);
        navigator.setPendingHitForFileDirection(-1);

        assertEquals(2, navigator.initialHitIndex());
    }

    private static SearchResultNavigator navigatorWithHits(int count) {
        SearchResultNavigator navigator = new SearchResultNavigator();
        navigator.setHits(java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new SearchResultNavigator.Hit(i, i + 1))
                .toList());
        return navigator;
    }
}
