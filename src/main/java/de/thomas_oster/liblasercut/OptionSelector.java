/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 *
 * LibLaserCut is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibLaserCut is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.
 *
 **/
package de.thomas_oster.liblasercut;

public class OptionSelector
{
  protected String[] items;
  protected String selectedItem;

  public OptionSelector(String[] items, String selectedItem)
  {
    this.items = items;
    this.selectedItem = selectedItem;
  }

  public OptionSelector(String[] items)
  {
    this.items = items;
    this.selectedItem = null;
  }

  public void setItems(String[] items)
  {
    this.items = items;
  }

  public String[] getItems()
  {
    return this.items;
  }

  public void setSelectedItem(String item)
  {
    this.selectedItem = item;
  }

  public void setSelectedItemIndex(int index)
  {
    this.selectedItem = this.items[index];
  }

  public String getSelectedItem()
  {
    return this.selectedItem;
  }

  @Override
  public String toString()
  {
    return this.selectedItem;
  }
}
