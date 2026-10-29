package com.hotspotband;

public final class Tile5GHz extends BandTile {
    @Override
    protected boolean is5GHz() {
        return true;
    }

    @Override
    protected Class<? extends BandTile> otherTile() {
        return Tile24GHz.class;
    }
}
