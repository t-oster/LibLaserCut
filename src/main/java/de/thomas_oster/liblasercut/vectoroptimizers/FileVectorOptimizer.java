/*
  This file is part of LibLaserCut.
  Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>

  LibLaserCut is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  LibLaserCut is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.

 */
package de.thomas_oster.liblasercut.vectoroptimizers;

import de.thomas_oster.liblasercut.VectorPart;
import java.util.List;

/**
 * Just returns the elements in the order they already appear: like
 * defined in the source file. It also preserves MOVETO commands and the
 * directions
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class FileVectorOptimizer extends VectorOptimizer
{

  @Override
  public VectorPart optimize(VectorPart vp)
  {
    return vp;
  }

  @Override
  protected List<Element> sort(List<Element> e)
  {
    return e;
  }
  
}
