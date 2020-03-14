/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2020 Max Gaukler (development@maxgaukler.de)
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


/**
 * Dummy implementation of ProgressListener that does nothing.
 */
public class ProgressListenerDummy implements ProgressListener
{
  @Override
  public void progressChanged(Object source, int percent)
  {
  }

  @Override
  public void taskChanged(Object source, String taskName)
  {
  }
  
}
