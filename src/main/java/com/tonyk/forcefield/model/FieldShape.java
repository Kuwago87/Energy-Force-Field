package com.tonyk.forcefield.model;

/**
 * A zone's geometry. CUBOID is the original rod-selected box (solid-filled
 * when raised). SPHERE is a beacon-generated bubble (a hollow 1-block-thick
 * shell when raised, not a solid ball - filling a large sphere solid would be
 * tens of millions of blocks and freeze the server).
 */
public enum FieldShape {
    CUBOID,
    SPHERE
}
