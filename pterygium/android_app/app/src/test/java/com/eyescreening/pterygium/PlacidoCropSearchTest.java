package com.eyescreening.pterygium;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PlacidoCropSearchTest {
    @Test public void originalCentreThirdIsFirst() {
        PlacidoCropSearch.Window first = PlacidoCropSearch.windows(4000, 3000).get(0);
        assertEquals(1500, first.left);
        assertEquals(1000, first.top);
        assertEquals(1000, first.side);
    }

    @Test public void searchCoversOffCentreRingsAndDifferentScales() {
        List<PlacidoCropSearch.Window> windows = PlacidoCropSearch.windows(4000, 3000);
        boolean upperLeft = false, lowerRight = false, wide = false;
        for (PlacidoCropSearch.Window w : windows) {
            upperLeft |= w.left == 0 && w.top == 0;
            lowerRight |= w.left + w.side == 4000 && w.top + w.side == 3000;
            wide |= w.side == 3000;
        }
        assertTrue(upperLeft && lowerRight && wide);
        assertTrue(windows.size() <= 12);
    }

    @Test public void cropsStaySquareInBoundsAndUnique() {
        for (int[] shape : new int[][]{{4000, 3000}, {3000, 4000}, {511, 513}, {1, 1}}) {
            List<PlacidoCropSearch.Window> windows = PlacidoCropSearch.windows(shape[0], shape[1]);
            for (int i = 0; i < windows.size(); i++) {
                PlacidoCropSearch.Window w = windows.get(i);
                assertTrue(w.side > 0 && w.left >= 0 && w.top >= 0);
                assertTrue(w.left + w.side <= shape[0]);
                assertTrue(w.top + w.side <= shape[1]);
                for (int j = 0; j < i; j++) assertFalse(w.sameAs(windows.get(j)));
            }
        }
    }

    @Test public void refinementsRecenterAndClampAtImageEdges() {
        PlacidoCropSearch.Window centred = PlacidoCropSearch.around(4000, 3000, 900, 700, 600);
        assertEquals(600, centred.left);
        assertEquals(400, centred.top);
        PlacidoCropSearch.Window edge = PlacidoCropSearch.around(4000, 3000, 3900, 2900, 9000);
        assertEquals(3000, edge.side);
        assertEquals(1000, edge.left);
        assertEquals(0, edge.top);
    }
}
