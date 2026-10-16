package ru.sarteam.allsearch;

import java.util.ArrayList;
import java.util.List;

/** Состояние и правила навигации по совпадениям текущего поиска. */
final class SearchResultNavigator {

    record Hit(int start, int end) {}

    record Navigation(Integer hitIndex, Integer fileDirection) {
        static Navigation hit(int index) {
            return new Navigation(index, null);
        }

        static Navigation file(int direction) {
            return new Navigation(null, direction);
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private int currentHitIndex = -1;
    private Integer pendingHitIndex;

    void setHits(List<Hit> newHits) {
        hits.clear();
        hits.addAll(newHits);
        currentHitIndex = -1;
    }

    void clear() {
        hits.clear();
        currentHitIndex = -1;
        pendingHitIndex = null;
    }

    List<Hit> hits() {
        return hits;
    }

    boolean isEmpty() {
        return hits.isEmpty();
    }

    int currentHitIndex() {
        return currentHitIndex;
    }

    Hit hitAt(int index) {
        return hits.get(index);
    }

    int initialHitIndex() {
        if (pendingHitIndex == null) {
            return 0;
        }
        return pendingHitIndex == Integer.MAX_VALUE
                ? (hits.isEmpty() ? 0 : hits.size() - 1)
                : pendingHitIndex;
    }

    void clearPendingHit() {
        pendingHitIndex = null;
    }

    void setPendingHitForFileDirection(int direction) {
        pendingHitIndex = direction > 0 ? 0 : Integer.MAX_VALUE;
    }

    Navigation next(int currentFileIndex, int fileCount) {
        if (hits.isEmpty()) {
            return null;
        }
        if (currentHitIndex >= 0 && currentHitIndex < hits.size() - 1) {
            return Navigation.hit(currentHitIndex + 1);
        }
        return fileCount > 1 ? Navigation.file(1) : Navigation.hit(0);
    }

    Navigation previous(int currentFileIndex, int fileCount) {
        if (hits.isEmpty()) {
            return null;
        }
        if (currentHitIndex > 0) {
            return Navigation.hit(currentHitIndex - 1);
        }
        return fileCount > 1 ? Navigation.file(-1) : Navigation.hit(hits.size() - 1);
    }

    int selectIndex(int index) {
        if (hits.isEmpty()) {
            currentHitIndex = -1;
            return -1;
        }
        currentHitIndex = Math.max(0, Math.min(hits.size() - 1, index));
        return currentHitIndex;
    }
}
