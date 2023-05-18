// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.mode.device;

import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.controller.ni.maschine_direct.mk3.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.daw.IBrowser;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Capability;
import de.mossgrabers.framework.daw.data.IBrowserColumn;
import de.mossgrabers.framework.daw.data.IBrowserColumnItem;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.StringUtils;

import java.util.Optional;


/**
 * Mode for navigating the browser.
 *
 * @author Jürgen Moßgraber and Kate Temkin
 */
public class DeviceBrowserMode extends BaseMode<IItem>
{
    private static final int SELECTION_OFF    = 0;
    private static final int SELECTION_PRESET = 1;
    private static final int SELECTION_FILTER = 2;

    private int              selectionMode;
    private int              filterColumn;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public DeviceBrowserMode (final MaschineControlSurface surface, final IModel model)
    {
        super ("Browser", surface, model);

        this.selectionMode = SELECTION_OFF;
        this.filterColumn = -1;
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        super.onDeactivate ();

        this.model.getBrowser ().stopBrowsing (true);
    }


    /**
     * Change the value of the last selected column.
     *
     * @param value The change value
     */
    public void changeSelectedColumnValue (final int value)
    {
        final int index = this.filterColumn == -1 ? 7 : this.filterColumn;
        this.changeValue (index, value);
    }


    /**
     * Set the last selected column to the selection column.
     */
    public void resetFilterColumn ()
    {
        this.filterColumn = -1;
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobValue (final int index, final int value)
    {
        if (!this.isKnobTouched (index))
            return;

		this.changeValue (index, value);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        // Make sure that only 1 knob gets changed in browse mode to prevent weird behavior
        if (this.isAnyKnobTouched () && !this.isKnobTouched (index))
            return;
        this.setTouchedKnob (index, isTouched);

        Optional<IBrowserColumn> fc;
        if (isTouched)
        {
            if (this.surface.isDeletePressed ())
            {
                this.surface.setTriggerConsumed (ButtonID.DELETE);
                fc = this.getFilterColumn (index);
                if (fc.isPresent () && fc.get ().doesExist ())
                    this.model.getBrowser ().resetFilterColumn (fc.get ().getIndex ());
                return;
            }
        }
        else
        {
            this.selectionMode = DeviceBrowserMode.SELECTION_OFF;
            return;
        }

        if (index == 7)
        {
            this.selectionMode = DeviceBrowserMode.SELECTION_PRESET;
            this.filterColumn = -1;
        }
        else
        {
            fc = this.getFilterColumn (index);
            if (fc.isPresent () && fc.get ().doesExist ())
            {
                this.selectionMode = DeviceBrowserMode.SELECTION_FILTER;
                this.filterColumn = fc.get ().getIndex ();
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay ()
    {
		final IGraphicDisplay display = this.surface.getGraphicsDisplay();
        final IBrowser browser = this.model.getBrowser ();
        if (!browser.isActive ())
            return;

        switch (this.selectionMode)
        {
            case DeviceBrowserMode.SELECTION_OFF:
                String selectedResult = browser.getSelectedResult ();
                selectedResult = selectedResult == null || selectedResult.isBlank () ? "Selection: None" : "Selection: " + selectedResult;
                for (int i = 0; i < 7; i++)
                {
                    final Optional<IBrowserColumn> column = this.getFilterColumn (i);
                    final String headerTopName = i == 0 ? browser.getInfoText () : "";
                    final String headerBottomName = i == 0 ? selectedResult : "";
                    final String menuBottomName = getColumnName (column);
                    display.addOptionElement (headerTopName, column.isEmpty () ? "" : column.get ().getName (), i == this.filterColumn, headerBottomName, menuBottomName, !menuBottomName.equals (" "), false);
                }

                final boolean supportsPreview = this.model.getHost ().supports (Capability.HAS_BROWSER_PREVIEW);
                final String bottomMenu = supportsPreview ? "Preview" : "";
                final ColorEx menuBottomColor = browser.isPreviewEnabled () ? ColorEx.ORANGE : ColorEx.GRAY;
                display.addOptionElement ("", browser.getSelectedContentType (), this.filterColumn == -1, null, "", bottomMenu, false, supportsPreview ? menuBottomColor : null, false);
                break;

            case DeviceBrowserMode.SELECTION_PRESET:
                final IBrowserColumnItem [] results = browser.getResultColumnItems ();

                if (!results[0].doesExist ())
                {
                    for (int i = 0; i < 8; i++)
                        display.addOptionElement (i == 3 ? "No results available..." : "", "", false, "", "", false, false);
                    return;
                }

                for (int i = 0; i < 8; i++)
                {
                    final String [] items = new String [6];
                    final boolean [] selected = new boolean [6];
                    for (int item = 0; item < 6; item++)
                    {
                        final int pos = i * 6 + item;
                        items[item] = pos < results.length ? results[pos].getName (14) : "";
                        selected[item] = pos < results.length && results[pos].isSelected ();
                    }
                    display.addListElement (items, selected);
                }
                break;

            case DeviceBrowserMode.SELECTION_FILTER:
                final IBrowserColumnItem [] item = browser.getFilterColumn (this.filterColumn).getItems ();
                for (int i = 0; i < 8; i++)
                {
                    final String [] items = new String [6];
                    final boolean [] selected = new boolean [6];
                    for (int itemIndex = 0; itemIndex < 6; itemIndex++)
                    {
                        final int pos = i * 6 + itemIndex;
                        final String hitText = " (" + item[pos].getHitCount () + ")";
                        String text = item[pos].getName (12 - hitText.length ());
                        if (!text.isEmpty ())
                            text = text + hitText;
                        items[itemIndex] = text;
                        selected[itemIndex] = item[pos].isSelected ();
                    }
                    display.addListElement (items, selected);
                }
                break;

            default:
                // Not used
                break;
        }

		display.send();
    }



    @Override
    public void selectPreviousItemPage ()
    {
        this.resetFilterColumn ();
        this.model.getBrowser ().previousContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItemPage ()
    {
        this.resetFilterColumn ();
        this.model.getBrowser ().nextContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItem ()
    {
        return this.model.getBrowser ().hasPreviousContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItem ()
    {
        return this.model.getBrowser ().hasNextContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItemPage ()
    {
        return this.hasPreviousItem ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItemPage ()
    {
        return this.hasNextItem ();
    }


    private Optional<IBrowserColumn> getFilterColumn (final int index)
    {
        final IBrowser browser = this.model.getBrowser ();
        int column = -1;
        final boolean [] browserDisplayFilter = this.surface.getConfiguration ().getBrowserDisplayFilter ();
        for (int i = 0; i < browser.getFilterColumnCount (); i++)
        {
            if (browserDisplayFilter[i])
            {
                column++;
                if (column == index)
                    return Optional.of (browser.getFilterColumn (i));
            }
        }
        return Optional.empty ();
    }


    private void selectNext (final int index, final int count)
    {
        final IBrowser browser = this.model.getBrowser ();
        if (index < 7)
        {
            final Optional<IBrowserColumn> fc = this.getFilterColumn (index);
            if (fc.isPresent () && fc.get ().doesExist ())
            {
                final int fi = fc.get ().getIndex ();
                if (fi < 0)
                    return;
                this.filterColumn = fi;
                for (int i = 0; i < count; i++)
                    browser.selectNextFilterItem (this.filterColumn);
            }
        }
        else
        {
            for (int i = 0; i < count; i++)
                browser.selectNextResult ();
        }
    }


    private void selectPrevious (final int index, final int count)
    {
        final IBrowser browser = this.model.getBrowser ();
        if (index < 7)
        {
            final Optional<IBrowserColumn> fc = this.getFilterColumn (index);
            if (fc.isPresent () && fc.get ().doesExist ())
            {
                final int fi = fc.get ().getIndex ();
                if (fi < 0)
                    return;
                this.filterColumn = fi;
                for (int j = 0; j < count; j++)
                    browser.selectPreviousFilterItem (this.filterColumn);
            }
        }
        else
        {
            for (int j = 0; j < count; j++)
                browser.selectPreviousResult ();
        }
    }


    private void changeValue (final int index, final int value)
    {
        int speed = this.model.getValueChanger ().calcSteppedKnobChange (value);
        final boolean direction = speed > 0;
        if (this.surface.isShiftPressed ())
            speed = speed * 4;

        speed = Math.abs (speed);
        if (direction)
            this.selectNext (index, speed);
        else
            this.selectPrevious (index, speed);
    }


    private static String getColumnName (final Optional<IBrowserColumn> column)
    {
        if (column.isEmpty () || !column.get ().doesCursorExist ())
            return "";
        final IBrowserColumn browserColumn = column.get ();
        return browserColumn.getCursorName ().equals (browserColumn.getWildcard ()) ? " " : browserColumn.getCursorName (12);
    }
}
