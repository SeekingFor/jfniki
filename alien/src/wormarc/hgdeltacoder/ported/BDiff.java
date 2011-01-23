/*
  BDiff.java -- port of bdiff.c
  Copyright 2010 Darrell Karbott.

  Ported from rev: 16f6c13706df of bdiff.c from:
  http://selenic.com/repo/hg-stable
  sha1sum: ef62bd826dbcd1b1b15329cd90afd18f8c67974f

  This is a quick and dirty Java port of bdiff.c from mercurial.
  It is a derived work, licensed under the GPL. See the original
  file header below.

  I used the gen_pointer_wrapper.py script to generate wrapper classes
  around primative arrays to support C pointer semantics so I could
  port with minimal changes to the source.

  This is admittedly kind of ugly, but I needed correct code
  and didn't have much time to spend writing it.

  This file was developed as component of
  "fniki" (a wiki implementation running over Freenet).
*/

/*
 bdiff.c - efficient binary diff extension for Mercurial

 Copyright 2005, 2006 Matt Mackall <mpm@selenic.com>

 This software may be used and distributed according to the terms of
 the GNU General Public License, incorporated herein by reference.

 Based roughly on Python difflib
*/

package wormarc.hgdeltacoder.ported;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import wormarc.hgdeltacoder.cptr.CBytePtr;
import wormarc.hgdeltacoder.cptr.CIntPtr;
import wormarc.hgdeltacoder.cptr.CHunkPtr;
import wormarc.hgdeltacoder.cptr.CLinePtr;
import wormarc.hgdeltacoder.cptr.CPosPtr;

public class BDiff {
    static class HunkList {
        CHunkPtr base;
        CHunkPtr head;
        public String toString() {
            return "{base=" + base + " head=" + head + "}";
        }
    };

    static final int splitlines(CBytePtr a, int len, CLinePtr lr) {
        // Copy to preserve pass by value semantics.
        a = a.copyPtr();
        // lr is passed by reference (**).

	int h, i;
        CBytePtr p = a.copyPtr();
        CBytePtr b = a.copyPtr();

        CBytePtr plast = a.copyPtrWithOffset(len - 1);

        Line l = new Line();

	/* count the lines */
	i = 1; /* extra line for sentinel */
        CBytePtr end = a.copyPtrWithOffset(len); // i.e. one past the last byte.
	for (/*NOP*/; !p.equals(end); p.plusPlus()) {
            if (p.deref() == '\n' || p.equals(plast)) {
                i++;
            }
        }

        try {
            lr.realloc(i); // Hinky
        }
        catch (OutOfMemoryError ome) {
            return -1;
        }

        lr = lr.copyPtr(); // Wacky. Point to the same rep, but don't modify the mPos for the caller.

        l = lr.deref();

	/* build the line array and calculate hashes */
	h = 0;
	for (p = a.copyPtr(); !p.equals(end); p.plusPlus()) {
            /* Leonid Yuriev's hash */
            h = (h * 1664525) + (0x000000FF & (int)(p.deref())) + 1013904223; // DCI: int math differences in Java?
            if (p.deref() == '\n' || p.equals(plast)) {
                l.h = h;
                h = 0;
                l.len = p.minus(b) + 1;
                l.l = b.copyPtr();
                l.n = Integer.MAX_VALUE;

                lr.plusPlus();
                l = lr.deref();
                b = p.copyPtrWithOffset(1);
            }
	}

	/* set up a sentinel */
	l.h = 0;
        l.len = 0;
	l.l = a.copyPtrWithOffset(len);
	return i - 1;
    }

    // 1 if not equal 0 otherwise.
    final static int cmp(Line a,  Line b) {
	if (a.h != b.h || a.len != b.len) {
            return 1;
        }

        // bdiff.c used memcmp, but mapped all non-zero values to 1
        // so this should be legit.

        // Dog slow. Arrays.equals really has no offset?
        // Reach into the rep.
        final byte[] aBytes = a.l.unsafeRep();
        final int aOffset = a.l.pos();
        final byte[] bBytes = b.l.unsafeRep();
        final int bOffset = b.l.pos();
        for (int index = 0; index < a.len; index++) {
            if (aBytes[index + aOffset] != bBytes[index +bOffset]) {
                return 1;
            }
        }
        return 0;
    }


    // FIXED BRACKET BUG IN THIS FUNCTION CHECK IT IN
    final static int equatelines(CLinePtr a, int an, CLinePtr b, int bn) {
        // Copy to preserve pass by value semantics.
        a = a.copyPtr();
        b = b.copyPtr();

	int i, j, buckets = 1, t, scale;
	CPosPtr h = null;

	/* build a hash table of the next highest power of 2 */
	while (buckets < bn + 1) {
		buckets *= 2;
        }

	/* try to allocate a large hash table to avoid collisions */
	for (scale = 4; scale > 0; scale /= 2) {
            try {
                // DCI: test. too brutal for Java?
                h = CPosPtr.alloc(scale * buckets);
            }
            catch (OutOfMemoryError ome) {
                /*NOP*/
            }
            if (h != null) {
                break;
            }
	}

	if (h == null) {
            return 0;
        }

	buckets = buckets * scale - 1;

	/* clear the hash table */
	for (i = 0; i <= buckets; i++) {
            h.bracket(i).pos = Integer.MAX_VALUE;
            h.bracket(i).len = 0;
	}

	/* add lines to the hash table chains */
	for (i = bn - 1; i >= 0; i--) {
            /* find the equivalence class */
            for (j = b.bracket(i).h & buckets; h.bracket(j).pos != Integer.MAX_VALUE;
                 j = (j + 1) & buckets) {
                if (0 == cmp(b.bracket(i), b.bracket(h.bracket(j).pos))) {
                    break;
                }
            }
            /* add to the head of the equivalence class */
            b.bracket(i).n = h.bracket(j).pos;
            b.bracket(i).e = j;
            h.bracket(j).pos = i;
            h.bracket(j).len++; /* keep track of popularity */
	}

	/* compute popularity threshold */
	t = (bn >= 4000) ? bn / 1000 : bn + 1; // DCI: math ok in Java?

	/* match items in a to their equivalence class in b */
	for (i = 0; i < an; i++) {
            /* find the equivalence class */
            for (j = a.bracket(i).h & buckets; h.bracket(j).pos != Integer.MAX_VALUE;
                 j = (j + 1) & buckets) {
                if (0 == cmp(a.bracket(i), b.bracket(h.bracket(j).pos))) {
                    break;
                }
            }
            a.bracket(i).e = j; /* use equivalence class for quick compare */
            if (h.bracket(j).len <= t) {
                a.bracket(i).n = h.bracket(j).pos; /* point to head of match list */
            }
            else {
                a.bracket(i).n = Integer.MAX_VALUE; /* too popular */
            }
        }

	/* discard hash tables */
        h.free(); // Pedantic. Not required.
	return 1;
    }

    static int longest_match(CLinePtr a, CLinePtr b, CPosPtr pos,
                             int a1, int a2, int b1, int b2, CIntPtr omi, CIntPtr omj) {
        // Copy to preserve pass by value semantics.
        a = a.copyPtr();
        b = b.copyPtr();
        pos = pos.copyPtr();
        // omi, omj are out parameters, passed by reference.

	int mi = a1, mj = b1, mk = 0, mb = 0, i, j, k;

	for (i = a1; i < a2; i++) {
            /* skip things before the current block */
            for (j = a.bracket(i).n; j < b1; j = b.bracket(j).n) {
                /*NOP*/
            }

            /* loop through all lines match a[i] in b */
            for (; j < b2; j = b.bracket(j).n) {
                /* does this extend an earlier match? */
                if (i > a1 && j > b1 && pos.bracket(j - 1).pos == i - 1) {
                    k = pos.bracket(j - 1).len + 1;
                } else {
                    k = 1;
                }

                pos.bracket(j).pos = i;
                pos.bracket(j).len = k;

                /* best match so far? */
                if (k > mk) {
                    mi = i;
                    mj = j;
                    mk = k;
                }
            }
	}

	if (mk != 0) {
            mi = mi - mk + 1;
            mj = mj - mk + 1;
	}

	/* expand match to include neighboring popular lines */
	while (mi - mb > a1 && mj - mb > b1 &&
	       a.bracket(mi - mb - 1).e == b.bracket(mj - mb - 1).e) {
            mb++;
        }
	while (mi + mk < a2 && mj + mk < b2 &&
	       a.bracket(mi + mk).e == b.bracket(mj + mk).e) {
            mk++;
        }

	omi.setValueAt(0, mi - mb);
	omj.setValueAt(0, mj - mb);

	return mk + mb;
    }

    static void recurse(CLinePtr a, CLinePtr b, CPosPtr pos,
		    int a1, int a2, int b1, int b2, HunkList l) {
        // Copy to preserve pass by value semantics.
        a = a.copyPtr();
        b = b.copyPtr();
        pos = pos.copyPtr();

	int i, j, k;

        // New allocation on each frame. NOT tunneled up the stack.
        CIntPtr ptrToI = CIntPtr.alloc(1);
        CIntPtr ptrToJ = CIntPtr.alloc(1);
	/* find the longest match in this chunk */
	k = longest_match(a, b, pos, a1, a2, b1, b2, ptrToI, ptrToJ);
        i = ptrToI.deref();
        j = ptrToJ.deref();
	if (k == 0) {
            return;
        }

	/* and recurse on the remaining chunks on either side */
	recurse(a, b, pos, a1, i, b1, j, l);
	l.head.deref().a1 = i;
	l.head.deref().a2 = i + k;
	l.head.deref().b1 = j;
	l.head.deref().b2 = j + k;
	l.head.plusPlus();
	recurse(a, b, pos, i + k, a2, j + k, b2, l);
    }

    static HunkList diff(CLinePtr a, int an, CLinePtr b, int bn) {
        // Copy to preserve pass by value semantics.
        a = a.copyPtr();
        b = b.copyPtr();

	HunkList l = new HunkList();
	CHunkPtr curr;
	CPosPtr pos;
	int t;

	/* allocate and fill arrays */
	t = equatelines(a, an, b, bn);
	pos = CPosPtr.alloc((bn != 0) ? bn : 1);

	/* we can't have more matches than lines in the shorter file */
        CHunkPtr allHunks = CHunkPtr.alloc((an<bn ? an:bn) + 1);
        l.head = allHunks.copyPtr();
        l.base = allHunks.copyPtr();

	if (/*pos && */ (l.base != null)  && (t != 0)) { // DCI: checking for calloc failures right?
            /* generate the matching block list */
            recurse(a, b, pos, 0, an, 0, bn, /*&*/l);
            l.head.deref().a1 = l.head.deref().a2 = an;
            l.head.deref().b1 = l.head.deref().b2 = bn;
            l.head.plusPlus();
	}

        pos.free(); // Pedantic, not required.

	/* normalize the hunk list, try to push each hunk towards the end */
	for (curr = l.base.copyPtr(); !curr.equals(l.head); curr.plusPlus()) {
            CHunkPtr next = curr.copyPtrWithOffset(1);
            int shift = 0;

            if (next.equals(l.head)) {
                break;
            }

            if (curr.deref().a2 == next.deref().a1) {
                while (curr.deref().a2 + shift < an && curr.deref().b2 + shift < bn
                       && 0 == cmp(a.copyPtrWithOffset(curr.deref().a2 + shift).deref(), // hmmm...object instantiations in loop
                                   b.copyPtrWithOffset(curr.deref().b2 + shift).deref())) {
                    shift++;
                }
            } else if (curr.deref().b2 == next.deref().b1) {
                while (curr.deref().b2 + shift < bn && curr.deref().a2 + shift < an
                       && 0 == cmp(b.copyPtrWithOffset(curr.deref().b2 + shift).deref(),
                                   a.copyPtrWithOffset(curr.deref().a2 + shift).deref())) {
                    shift++;
                }
            }

            if (shift == 0) {
                continue;
            }
            curr.deref().b2 += shift;
            next.deref().b1 += shift;
            curr.deref().a2 += shift;
            next.deref().a1 += shift;
        }

        return l;
    }

    public static byte[] bdiff(byte[] saBytes, byte[] sbBytes) {
	CBytePtr sa;
        CBytePtr sb;
	//PyObject *result = NULL;
	CLinePtr al = CLinePtr.alloc(0); // splitlines reallocates
        CLinePtr bl = CLinePtr.alloc(0); // splitlines reallocates
	HunkList l;
	CHunkPtr h;
	//CBytePtr encode = new CBytePtr(new byte[12]);
        CBytePtr rb;
	int an, bn, len = 0, la, lb;

        sa = new CBytePtr(saBytes);
        la = saBytes.length;
        sb = new CBytePtr(sbBytes);
        lb = sbBytes.length;

	an = splitlines(sa, la, al);
	bn = splitlines(sb, lb, bl);

        //if (!al || !bl)   // Was there a bug in the .c? i.e. splitlines
        //      goto nomem; // on malloc failure.
        // This was just trapping malloc failure, don't need it, java throws

	l = diff(al, an, bl, bn);

	if (l.head == null) {
            //return null;
            return new byte[0];
        }
	/* calculate length of output */
	la = lb = 0;
	for (h = l.base.copyPtr(); !h.equals(l.head); h.plusPlus()) {
            if (h.deref().a1 != la || h.deref().b1 != lb) {
                len += 12 + bl.bracket(h.deref().b1).l.minus(bl.bracket(lb).l);
            }
            la = h.deref().a2;
            lb = h.deref().b2;
	}

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream dataOut = new DataOutputStream(buffer);

	la = lb = 0;

        try {
            for (h = l.base.copyPtr(); !h.equals(l.head); h.plusPlus()) {
                if (h.deref().a1 != la || h.deref().b1 != lb) {
                    len = bl.bracket(h.deref().b1).l.minus(bl.bracket(lb).l);

                    // This is just writing 3 unsigned 32 bit integers into a block
                    // of memory in netork byte order, right?
                    //*(uint32_t *)(encode)     = htonl(al[la].l - al->l);
                    //*(uint32_t *)(encode + 4) = htonl(al[h->a1].l - al->l);
                    //*(uint32_t *)(encode + 8) = htonl(len);

                    int value = al.bracket(la).l.minus(al.deref().l);
                    if (value < 0) { throw new RuntimeException("overflow in encoding???"); }
                    dataOut.writeInt(value);

                    value = al.bracket(h.deref().a1).l.minus(al.deref().l);
                    if (value < 0) { throw new RuntimeException("overflow in encoding???"); }
                    dataOut.writeInt(value);

                    value = len;
                    if (value < 0) { throw new RuntimeException("overflow in encoding???"); }
                    dataOut.writeInt(value);

                    CBytePtr linePtr = bl.bracket(lb).l;
                    dataOut.write(linePtr.unsafeRep(), linePtr.pos(), len);
                }
                la = h.deref().a2;
                lb = h.deref().b2;
            }
            dataOut.close();
        }
        catch (IOException ioe) {
            throw new RuntimeException("Unexpected exception encoding patch: " + ioe);
        }
        return buffer.toByteArray();
    }
}