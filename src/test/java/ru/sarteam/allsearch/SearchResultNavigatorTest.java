package ru.sarteam.allsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class SearchResultNavigatorTest {

    @Test
    void movesBetweenHitsBeforeChangingFile() {
        SearchResultNavigator navigator = navigatorWithHits(3);
        navigator.selectIndex(0);

        SearchResultNavigator.Navigation next = navigator.next(0, 2);

        assertEquals(1, next.hitIndex());
        assertNull(next.fileIndex());
    }

    @Test
    void movesToNextFileAfterLastHit() {
        SearchResultNavigator navigator = navigatorWithHits(2);
        navigator.selectIndex(1);

        SearchResultNavigator.Navigation next = navigator.next(0, 2);

        assertEquals(1, next.fileDirection());
        assertEquals(1, next.fileIndex());
        assertNull(next.hitIndex());
    }

    @Test
    void wrapsInsideSingleFile() {
        SearchResultNavigator navigator = navigatorWithHits(2);
        navigator.selectIndex(0);

        SearchResultNavigator.Navigation previous = navigator.previous(0, 1);

        assertEquals(1, previous.hitIndex());
        assertNull(previous.fileIndex());
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

    @Test
    void wrapsAcrossFileBoundariesAndPreservesPendingSelection() {
        SearchResultNavigator navigator = navigatorWithHits(2);
        navigator.selectIndex(1);
        assertEquals(0, navigator.next(2, 3).fileIndex());
        navigator.setHits(List.of(new SearchResultNavigator.Hit(4, 6)));
        assertEquals(0, navigator.initialHitIndex());
        navigator.selectIndex(0);
        assertEquals(2, navigator.previous(0, 3).fileIndex());
        navigator.setHits(List.of(new SearchResultNavigator.Hit(0, 1), new SearchResultNavigator.Hit(2, 3)));
        assertEquals(1, navigator.initialHitIndex());
    }

    @Test
    void protectsHitsAndClearsNavigationState() {
        SearchResultNavigator navigator = navigatorWithHits(2);
        assertThrows(UnsupportedOperationException.class, () -> navigator.hits().clear());
        navigator.setPendingHitForFileDirection(-1);
        navigator.clear();
        assertNull(navigator.next(0, 2));
        assertNull(navigator.previous(0, 2));
        assertEquals(-1, navigator.currentHitIndex());
        assertEquals(0, navigator.initialHitIndex());
    }

    private static SearchResultNavigator navigatorWithHits(int count) {
        SearchResultNavigator navigator = new SearchResultNavigator();
        navigator.setHits(java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new SearchResultNavigator.Hit(i, i + 1))
                .toList());
        return navigator;
    }
}
