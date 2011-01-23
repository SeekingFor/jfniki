/* Helper functions used to partition an Archive's HistoryLinks into Blocks.
 *
 *  Copyright (C) 2010, 2011 Darrell Karbott
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.0 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 *  Author: djk@isFiaD04zgAgnrEC5XJt1i4IE7AkNPqhBG5bONi6Yks
 *
 *  This file was developed as component of
 * "fniki" (a wiki implementation running over Freenet).
 */

package wormarc;

// DCI: unit test this!

import java.util.ArrayList;
import java.util.List;

// DCI: javadoc
public class PartitioningMath {
    public static final class Partition {
        final int mStart;
        final int mEnd;
        final long mLength;

        public Partition(int start, int end, long length) {
            mStart = start;
            mEnd = end;
            mLength = length;
        }

        int getStart() { return mStart; }
        int getEnd() { return mEnd; }
        long getLength() { return mLength; }
    }

    public static boolean isOrdered(List<Partition> partitions) {
        List<Long> lengths = new ArrayList<Long>();
        for (Partition partition : partitions) {
            lengths.add(partition.getLength());
        }

        // Ignore trailing 0 length blocks.
        while (lengths.size() > 0 && lengths.get(lengths.size() -1) == 0) {
            lengths.remove(lengths.size() -1);
        }

        for (int index = 0; index < lengths.size() - 1; index++) {
            if (lengths.get(index) > lengths.get(index + 1)) {
                return false;
            }
        }
        return true;
    }

    // Underlying block list is newest to oldest.
    // Partitions list is newest to oldest.
    public static boolean isContiguous(List<Partition> partitions) {
        if (partitions.size() == 0) {
            return true;
        }

        // DCI: copied from python. Why again?
        Partition lastPartition = partitions.get(partitions.size() - 1);
        if (lastPartition.getStart() > lastPartition.getEnd()) {
            return false;
        }

        for (int index = 0; index < partitions.size() - 1; index++) {
            Partition current = partitions.get(index);
            if (current.getStart() > current.getEnd()) {
                return false;
            }
            Partition next = partitions.get(index + 1);
            int span = next.getStart() - current.getEnd();
            if (span < 0  || span > 1) {
                return false;
            }
        }
        return true;
    }

    static void assertTrue(boolean condition) {
        if (condition) { return; }
        throw new RuntimeException("Assertion failure in BlockPartitionMath");
    }

    // Current is younger than next.
    public static List<Partition> repartition(List<Partition> partitions, int multiple) {
        for (int index = 0; index < partitions.size() - 1; index++) {
            Partition current = partitions.get(index);
            Partition next = partitions.get(index + 1);
            if (current.getLength() * multiple >= next.getLength()) {
                List<Partition> good = new ArrayList<Partition>(partitions.subList(0, index));
                List<Partition> rest = new ArrayList<Partition>(partitions.subList(index, partitions.size()));
                Partition restHead = rest.get(0);
                Partition restNext = rest.get(1);
                // inherited from python code.
                //assertTrue((restNext.getStart() - restHead.getEnd() >= 0) && // In order, makes sense.
                //           (restNext.getStart() - restHead.getEnd() < 2));   // I can't remember why. ???
                assertTrue(restNext.getStart() - restHead.getEnd() >= 0);
                rest.set(1,  new Partition(restHead.getStart(), restNext.getEnd(),
                                           restHead.getLength() + restNext.getLength()));
                rest.remove(0);
                good.addAll(repartition(rest, multiple)); // DCI: bug in python here
                assertTrue(isOrdered(good));
                // assertTrue(is_contiguous(godd)); // Removed this constraint. Can drop empty partitions.
                return good;
            }
        }

        List<Partition> ret = new ArrayList<Partition> (partitions);
        assertTrue(isOrdered(ret));
        return ret;
    }

    public static List<Partition> compress(List<Partition> partitions, int maxLen, int multiple) {
        List<Partition> nonZeroLength = new ArrayList<Partition> ();
        for (Partition partition : partitions) {
            if (partition.getLength() > 0) {
                nonZeroLength.add(partition);
            }
        }
         // Deep copy is important.
        partitions = nonZeroLength;

        if (partitions.size() <= maxLen) {
            // Doesn't repartition if it didn't compress.
            return partitions;
        }

        assertTrue(maxLen > 1);

        while (partitions.size() > maxLen) {
            Partition head = partitions.get(0);
            Partition next = partitions.get(1);
            Partition combined = new Partition(head.getStart(), next.getEnd(),
                                               head.getLength() + next.getLength());
            partitions.set(1, combined);
            partitions = new ArrayList<Partition> (partitions.subList(1, partitions.size()));
            // Enforce the ordering constraint.
            partitions = repartition(partitions, multiple);
        }
        assertTrue(isOrdered(partitions));

        return partitions;
    }

    ////////////////////////////////////////////////////////////
    // Debug helper methods.
    public static String toString(List<Partition> partitions) {
        if (partitions.isEmpty()) {
            return "()";
        }
        StringBuilder buf = new StringBuilder();
        buf.append("(");

        boolean first  = true;
        for (Partition partition : partitions) {
            if (!first) {
                buf.append(", ");
            }
            first = false;
            buf.append("(");
            buf.append(partition.mStart);
            buf.append(", ");
            buf.append(partition.mEnd);
            buf.append(", ");
            buf.append(partition.mLength);
            buf.append(")");
        }

        buf.append(")");

        return buf.toString();
    }

    public static boolean equal(List<Partition> listA, List<Partition> listB) {
        if (listA.size() != listB.size()) {
            return false;
        }
        for (int index = 0; index < listA.size(); index++) {
            Partition a = listA.get(index);
            Partition b = listB.get(index);
            if ((a.mStart != b.mStart) ||
                (a.mEnd != b.mEnd) ||
                (a.mLength != b.mLength))  {
                return false;
            }

        }
        return true;
    }
}
