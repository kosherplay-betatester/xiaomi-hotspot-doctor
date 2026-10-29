package com.hotspotband;

public final class Tile24GHz extends BandTile {
    @Override
    protected boolean is5GHz() {
        return false;
    }

    @Override
    protected Class<? extends BandTile> otherTile() {
        return Tile5GHz.class;
    }
}
