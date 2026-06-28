package ai.koryki.iql.functions;

import ai.koryki.catalog.schema.types.CoreTypeFamily;
import ai.koryki.catalog.schema.types.EncodingLattice;
import ai.koryki.catalog.schema.types.EpochTypeEncoding;
import ai.koryki.catalog.schema.types.NativeEncoding;
import ai.koryki.catalog.schema.types.TypeDescriptor;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step 1 — conditional-branch reconciliation: numeric widening + lossless epoch encoding. */
class ConditionalReconcilerTest {

    private static TypeDescriptor epoch(ChronoUnit unit) {
        return new TypeDescriptor("BIGINT", new EpochTypeEncoding(unit), CoreTypeFamily.TIMESTAMP);
    }

    @Test
    void numericBranchesWidenToFloatNative() {
        ConditionalReconciler.Result r = ConditionalReconciler.reconcile(
                List.of(TypeDescriptor.INTEGER, TypeDescriptor.DECIMAL, TypeDescriptor.FLOAT));

        assertEquals(CoreTypeFamily.FLOAT, r.target().getTypeFamily());
        assertEquals(NativeEncoding.of(CoreTypeFamily.FLOAT), r.target().getTypeEncoding());
        // Numeric widening is implicit in CASE/COALESCE — no explicit per-branch conversion.
        assertTrue(r.perBranch().stream().allMatch(ConditionalReconciler.Conversion::isIdentity));
    }

    @Test
    void epochSecondsAndMillisUnifyToMillis() {
        ConditionalReconciler.Result r = ConditionalReconciler.reconcile(
                List.of(epoch(ChronoUnit.SECONDS), epoch(ChronoUnit.MILLIS)));

        assertEquals(CoreTypeFamily.TIMESTAMP, r.target().getTypeFamily());
        assertEquals(new EpochTypeEncoding(ChronoUnit.MILLIS), r.target().getTypeEncoding());
        assertFalse(r.perBranch().get(0).isIdentity(), "SECONDS branch must convert");
        assertTrue(r.perBranch().get(1).isIdentity(), "MILLIS branch is already the target");
    }

    @Test
    void nullLiteralBranchIsIgnored() {
        ConditionalReconciler.Result r = ConditionalReconciler.reconcile(
                List.of(TypeDescriptor.NULL, epoch(ChronoUnit.MILLIS)));

        assertEquals(new EpochTypeEncoding(ChronoUnit.MILLIS), r.target().getTypeEncoding());
        assertTrue(r.perBranch().get(0).isIdentity(), "NULL literal needs no conversion");
        assertTrue(r.perBranch().get(1).isIdentity());
    }

    @Test
    void dateAndTimestampWidenToTimestamp() {
        ConditionalReconciler.Result r = ConditionalReconciler.reconcile(
                List.of(TypeDescriptor.DATE, TypeDescriptor.TIMESTAMP));

        assertEquals(CoreTypeFamily.TIMESTAMP, r.target().getTypeFamily());
        assertEquals(NativeEncoding.of(CoreTypeFamily.TIMESTAMP), r.target().getTypeEncoding());
    }

    @Test
    void timeDoesNotWidenWithDate() {
        assertThrows(ConditionalReconciler.ReconcileException.class, () ->
                ConditionalReconciler.reconcile(List.of(TypeDescriptor.TIME, TypeDescriptor.DATE)));
    }

    @Test
    void differentFamilyGroupsAreAHardError() {
        assertThrows(ConditionalReconciler.ReconcileException.class, () ->
                ConditionalReconciler.reconcile(List.of(TypeDescriptor.TIME, TypeDescriptor.INTEGER)));
    }

    @Test
    void noLosslessCommonEncodingIsAHardError() {
        TypeDescriptor nativeTs = new TypeDescriptor("TIMESTAMP", null, CoreTypeFamily.TIMESTAMP);
        assertThrows(ConditionalReconciler.ReconcileException.class, () ->
                ConditionalReconciler.reconcile(List.of(epoch(ChronoUnit.SECONDS), nativeTs)));
    }

    @Test
    void latticeConvertsCoarserEpochToFinerByScale() {
        EpochTypeEncoding secs = new EpochTypeEncoding(ChronoUnit.SECONDS);
        EpochTypeEncoding millis = new EpochTypeEncoding(ChronoUnit.MILLIS);

        assertEquals("(t) * 1000", EncodingLattice.convertSql("t", secs, millis));
        assertEquals("t", EncodingLattice.convertSql("t", millis, millis));
        assertEquals(1, EncodingLattice.cost(secs, millis));
        assertEquals(0, EncodingLattice.cost(millis, millis));
    }
}
