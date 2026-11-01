package com.bettercontent.bettercooking.drinks.placed;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CupQueueTest {
    @Test void fourDifferentActualOriginalsAreFifoNotFourCopiesOfFirst(){
        var queue=CupQueue.<String>empty().append("water/A/age100").append("milk/B/age9").append("water/C/sauce").append("milk/D/volume");
        for(String expected:List.of("water/A/age100","milk/B/age9","water/C/sauce","milk/D/volume")){
            assertEquals(expected,queue.head().orElseThrow());queue=queue.tail();
        }assertEquals(0,queue.size());assertThrows(IllegalStateException.class,queue::head);
    }
    @Test void legacySentinelsStayUnknownWhenManagedOriginalAdded(){
        var queue=CupQueue.<String>legacy(3).append("actual-managed");
        for(int i=0;i<3;i++){assertTrue(queue.head().isEmpty());queue=queue.tail();}
        assertEquals("actual-managed",queue.head().orElseThrow());
    }
    @Test void appendIsDetachedAndCannotOverflowOrOverwrite(){
        var original=CupQueue.<String>legacy(3);var full=original.append("new");assertEquals(3,original.size());
        assertEquals(4,full.size());assertThrows(IllegalStateException.class,()->full.append("fifth"));
        assertThrows(UnsupportedOperationException.class,()->full.entries().clear());
    }
    @Test void countMismatchNeverBecomesLegacyOrClamped(){
        var managed=CupQueue.<String>empty().append("paid");
        assertThrows(IllegalStateException.class,()->managed.requireCount(2));
        assertThrows(IllegalStateException.class,()->CupQueue.legacy(5));
        assertThrows(IllegalStateException.class,()->CupQueue.legacy(-1));
        assertThrows(IllegalStateException.class,()->new CupQueue<>(Collections.nCopies(5,Optional.of("paid"))));
    }
    @Test void constructorDetachesCallerList(){
        var source=new ArrayList<Optional<String>>();source.add(Optional.of("a"));var queue=new CupQueue<>(source);
        source.clear();assertEquals("a",queue.head().orElseThrow());
    }
    public static void main(String[] args){var controls=new CupQueueTest();controls.fourDifferentActualOriginalsAreFifoNotFourCopiesOfFirst();controls.legacySentinelsStayUnknownWhenManagedOriginalAdded();controls.appendIsDetachedAndCannotOverflowOrOverwrite();controls.countMismatchNeverBecomesLegacyOrClamped();controls.constructorDetachesCallerList();System.out.println("PASS 5 pure FIFO controls (not native acceptance)");}
}
