package de.blau.android.easyedit;

import java.util.List;
import java.util.Set;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import de.blau.android.R;
import de.blau.android.easyedit.turnrestriction.FromElementActionModeCallback;
import de.blau.android.osm.Node;
import de.blau.android.osm.OsmElement;
import de.blau.android.osm.Tags;
import de.blau.android.osm.Way;
import de.blau.android.util.Geometry;
import de.blau.android.util.Snack;
import de.blau.android.util.ThemeUtils;
import de.blau.android.util.Util;

public class WaySelectionActionModeCallback extends ElementSelectionActionModeCallback {
    private static final String DEBUG_TAG                  = "WaySelectionAction...";
    private static final int    MENUITEM_SPLIT             = 10;
    private static final int    MENUITEM_MERGE             = 11;
    private static final int    MENUITEM_REVERSE           = 12;
    private static final int    MENUITEM_APPEND            = 13;
    private static final int    MENUITEM_RESTRICTION       = 14;
    private static final int    MENUITEM_ROTATE            = 15;
    private static final int    MENUITEM_ORTHOGONALIZE     = 16;
    private static final int    MENUITEM_CIRCULIZE         = 17;
    private static final int    MENUITEM_SPLIT_POLYGON     = 18;
    private static final int    MENUITEM_ADDRESS           = 19;
    private static final int    MENUITEM_UNJOIN            = 20;
    private static final int    MENUITEM_UNJOIN_DISSIMILAR = 21;
    private static final int    MENUITEM_REMOVE_NODE       = 22;
    private static final int    MENUITEM_EXTRACT_SEGMENT   = 23;

    private Set<OsmElement> cachedMergeableWays;
    private Set<OsmElement> cachedAppendableNodes;
    private Set<OsmElement> cachedViaElements;
    private MenuItem        addressItem;
    private MenuItem        splitItem;
    private MenuItem        mergeItem;
    private MenuItem        appendItem;
    private MenuItem        restrictionItem;
    private MenuItem        orthogonalizeItem;
    private MenuItem        circulizeItem;
    private MenuItem        splitPolygonItem;
    private MenuItem        removeNodeItem;
    private MenuItem        unjoinItem;
    private MenuItem        unjoinDissimilarItem;
    private MenuItem        extractSegmentItem;
    private String          orthogonalizeTitle;

    /**
     * Construct a new ActionModeCallback
     * 
     * @param manager the EasyEditManager instance
     * @param way the selected Way
     */
    WaySelectionActionModeCallback(@NonNull EasyEditManager manager, @NonNull Way way) {
        super(manager, way);
        Log.d(DEBUG_TAG, "constructor");
        findConnectedWays(way);
    }

    /**
     * Find the Ways in different categories that are connected to this Way
     * 
     * @param way the selected Way
     */
    private void findConnectedWays(@NonNull Way way) {
        cachedMergeableWays = findMergeableWays(way);
        cachedAppendableNodes = findAppendableNodes(way);
        cachedViaElements = findViaElements(way);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        helpTopic = R.string.help_wayselection;
        super.onCreateActionMode(mode, menu);
        Log.d(DEBUG_TAG, "onCreateActionMode");
        logic.setSelectedWay((Way) element);
        main.invalidateMap();
        mode.setTitle(R.string.actionmode_wayselect);
        mode.setSubtitle(null);

        menu = replaceMenu(menu, mode, this);

        addressItem = menu.add(Menu.NONE, MENUITEM_ADDRESS, Menu.NONE, R.string.tag_menu_address)
                .setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_address));

        menu.add(Menu.NONE, MENUITEM_REVERSE, Menu.NONE, R.string.menu_reverse).setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_reverse));

        splitItem = menu.add(Menu.NONE, MENUITEM_SPLIT, Menu.NONE, R.string.menu_split).setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_split));

        mergeItem = menu.add(Menu.NONE, MENUITEM_MERGE, Menu.NONE, R.string.menu_merge).setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_merge));

        appendItem = menu.add(Menu.NONE, MENUITEM_APPEND, Menu.NONE, R.string.menu_append).setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_append));

        restrictionItem = menu.add(Menu.NONE, MENUITEM_RESTRICTION, Menu.NONE, R.string.actionmode_restriction)
                .setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_add_restriction));

        orthogonalizeTitle = main.getString(R.string.menu_orthogonalize);
        orthogonalizeItem = menu.add(Menu.NONE, MENUITEM_ORTHOGONALIZE, Menu.NONE, orthogonalizeTitle)
                .setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_ortho));

        menu.add(Menu.NONE, MENUITEM_ROTATE, Menu.NONE, R.string.menu_rotate).setIcon(ThemeUtils.getResIdFromAttribute(main, R.attr.menu_rotate));

        circulizeItem = menu.add(Menu.NONE, MENUITEM_CIRCULIZE, Menu.NONE, R.string.menu_circulize);

        splitPolygonItem = menu.add(Menu.NONE, MENUITEM_SPLIT_POLYGON, Menu.NONE, R.string.menu_split_polygon);

        removeNodeItem = menu.add(Menu.NONE, MENUITEM_REMOVE_NODE, Menu.NONE, R.string.menu_remove_node_from_way);

        unjoinItem = menu.add(Menu.NONE, MENUITEM_UNJOIN, Menu.NONE, R.string.menu_unjoin);
        unjoinDissimilarItem = menu.add(Menu.NONE, MENUITEM_UNJOIN_DISSIMILAR, Menu.NONE, R.string.menu_unjoin_dissimilar);

        extractSegmentItem = menu.add(Menu.NONE, MENUITEM_EXTRACT_SEGMENT, Menu.NONE, R.string.menu_extract_segment);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu = replaceMenu(menu, mode, this);
        boolean updated = super.onPrepareActionMode(mode, menu);
        Log.d(DEBUG_TAG, "onPrepareActionMode");

        Way way = (Way) element;
        int size = way.getNodes().size();
        boolean closed = way.isClosed();

        updated |= setItemVisibility(way.hasTagKey(Tags.KEY_BUILDING) && !way.hasTagKey(Tags.KEY_ADDR_HOUSENUMBER), addressItem, false);

        updated |= setItemVisibility(size > 2, splitItem, false);

        updated |= setItemVisibility(!cachedMergeableWays.isEmpty(), mergeItem, false);

        updated |= setItemVisibility(!cachedAppendableNodes.isEmpty(), appendItem, false);

        updated |= setItemVisibility(way.getTagWithKey(Tags.KEY_HIGHWAY) != null && !cachedViaElements.isEmpty(), restrictionItem, false);

        updated |= setItemVisibility(size > 2, orthogonalizeItem, false);
        if (orthogonalizeItem.isVisible()) {
            if (closed) {
                if (!orthogonalizeItem.getTitle().equals(orthogonalizeTitle)) {
                    orthogonalizeItem.setTitle(orthogonalizeTitle);
                    updated = true;
                }
            } else {
                if (orthogonalizeItem.getTitle().equals(orthogonalizeTitle)) {
                    orthogonalizeItem.setTitle(R.string.menu_straighten);
                    updated = true;
                }
            }
        }

        updated |= setItemVisibility(size > 3 && closed, circulizeItem, false);

        // 5 nodes is the minimum required to be able to split in to two polygons
        updated |= setItemVisibility(size > 4 && closed, splitPolygonItem, false);

        updated |= setItemVisibility(size >= 4 || (!closed && size >= 3), removeNodeItem, false);

        boolean joined = isJoined(way);
        updated |= setItemVisibility(joined, unjoinItem, false);
        updated |= setItemVisibility(joined, unjoinDissimilarItem, false);

        updated |= setItemVisibility(size >= 3 && !closed, extractSegmentItem, false);

        if (updated) {
            arrangeMenu(menu);
        }
        return updated;
    }

    /**
     * Reverse a way showing a confirmation dialog if it is not reversible without changing semantics
     */
    private void reverseWay() {
        final Way way = (Way) element;
        if (way.notReversable()) {
            new AlertDialog.Builder(main).setTitle(R.string.menu_reverse).setMessage(R.string.notreversable_description)
                    .setPositiveButton(R.string.reverse_anyway, (dialog, which) -> {
                        if (logic.performReverse(main, way)) { // true if it had oneway tag
                            Snack.barWarning(main, R.string.toast_oneway_reversed);
                            main.performTagEdit(way, null, false, false);
                        }
                    }).show();
        } else if (logic.performReverse(main, way)) { // true if it had oneway tag
            Snack.barWarning(main, R.string.toast_oneway_reversed);
            main.performTagEdit(way, null, false, false);
        } else {
            manager.invalidate(); // successful reversal update menubar
        }
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (!super.onActionItemClicked(mode, item)) {
            Way way = (Way) element;
            switch (item.getItemId()) {
            case MENUITEM_SPLIT:
                main.startSupportActionMode(new WaySplittingActionModeCallback(manager, way, false));
                break;
            case MENUITEM_MERGE:
                main.startSupportActionMode(new WayMergingActionModeCallback(manager, way, cachedMergeableWays));
                break;
            case MENUITEM_REVERSE:
                reverseWay();
                findConnectedWays(way);
                break;
            case MENUITEM_APPEND:
                main.startSupportActionMode(new WayAppendingActionModeCallback(manager, way, cachedAppendableNodes));
                break;
            case MENUITEM_RESTRICTION:
                main.startSupportActionMode(new FromElementActionModeCallback(manager, way, cachedViaElements));
                break;
            case MENUITEM_ROTATE:
                deselect = false;
                main.startSupportActionMode(new WayRotationActionModeCallback(manager, way));
                break;
            case MENUITEM_ORTHOGONALIZE:
                logic.performOrthogonalize(main, way);
                manager.invalidate();
                break;
            case MENUITEM_CIRCULIZE:
                logic.performCirculize(main, way);
                manager.invalidate();
                break;
            case MENUITEM_SPLIT_POLYGON:
                main.startSupportActionMode(new WaySplittingActionModeCallback(manager, way, true));
                break;
            case MENUITEM_ADDRESS:
                main.performTagEdit(element, null, true, false);
                break;
            case MENUITEM_REMOVE_NODE:
                main.startSupportActionMode(new RemoveNodeFromWayActionModeCallback(manager, way));
                break;
            case MENUITEM_UNJOIN:
            case MENUITEM_UNJOIN_DISSIMILAR:
                logic.performUnjoinWay(main, way, MENUITEM_UNJOIN_DISSIMILAR == item.getItemId());
                break;
            case MENUITEM_SHARE_POSITION:
                Util.sharePosition(main, Geometry.centroidLonLat(way), main.getMap().getZoomLevel());
                break;
            case MENUITEM_EXTRACT_SEGMENT:
                main.startSupportActionMode(new WaySegmentActionModeCallback(manager, way));
                break;
            default:
                return false;
            }
        }
        return true;
    }

    @Override
    protected void menuDelete(final ActionMode mode) {
        boolean isRelationMember = element.hasParentRelations();
        boolean allNodesDownloaded = logic.isInDownload((Way) element);
        if (allNodesDownloaded) {
            if (isRelationMember) {
                new AlertDialog.Builder(main).setTitle(R.string.delete).setMessage(R.string.deleteway_relation_description)
                        .setPositiveButton(R.string.deleteway_wayandnodes, (dialog, which) -> deleteWay(mode)).setNeutralButton(R.string.cancel, null).show();
            } else {
                deleteWay(mode);
            }
        } else {
            new AlertDialog.Builder(main).setTitle(R.string.delete).setMessage(R.string.deleteway_nodesnotdownloaded_description)
                    .setPositiveButton(R.string.okay, null).show();
        }
    }

    /**
     * Delete way including orphan nodes and finish this mode
     * 
     * @param mode the current ActionMode
     */
    private void deleteWay(final ActionMode mode) {
        logic.performEraseWay(main, (Way) element, true, true);
        if (mode != null) {
            mode.finish();
        }
    }

    /**
     * Check for at least one Node that is shared with another Way
     * 
     * @param way the way
     * @return true if there is at least one shared node
     */
    private boolean isJoined(@NonNull Way way) {
        for (Node node : way.getNodes()) {
            List<Way> ways = logic.getWaysForNode(node);
            if (ways.size() > 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void update() {
        findConnectedWays((Way) element);
    }

    @Override
    public boolean processShortcut(Character c) {
        if (c == Util.getShortCut(main, R.string.shortcut_square)) {
            logic.performOrthogonalize(main, (Way) element);
            manager.invalidate();
            return true;
        }
        return super.processShortcut(c);
    }
}
