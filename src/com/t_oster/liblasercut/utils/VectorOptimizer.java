package com.t_oster.liblasercut.utils;

import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.VectorCommand;
import com.t_oster.liblasercut.VectorPart;
import com.t_oster.liblasercut.platform.Point;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class VectorOptimizer
{
  public enum OrderStrategy
  {
    FILE,
    //INNER_FIRST,
    NEAREST
  }
  
  class Element
  {
    LaserProperty prop;
    Point start;
    List<Point> moves = new LinkedList<Point>();
    Point getEnd()
    {
      return moves.isEmpty() ? start : moves.get(moves.size()-1);
    }
  }
  
  private OrderStrategy strategy = OrderStrategy.FILE;
  
  public VectorOptimizer(OrderStrategy s)
  {
    this.strategy = s;
  }
  
  private List<Element> divide(VectorPart vp)
  {
    List<Element> result = new LinkedList<Element>();
    Element cur = null;
    Point lastMove = null;
    LaserProperty lastProp = null;
    boolean stop = false;
    for (VectorCommand cmd : vp.getCommandList())
    {
      switch (cmd.getType())
      {
        case MOVETO:
        {
          lastMove = new Point(cmd.getX(), cmd.getY());
          stop = true;
          break;
        }
        case LINETO:
        {
          if (stop)
          {
            stop = false;
            if (cur != null)
            {
              result.add(cur);
            }
            cur = new Element();
            cur.start = lastMove;
            cur.prop = lastProp;
          }
          cur.moves.add(new Point(cmd.getX(), cmd.getY()));
          break;
        }
        case SETPROPERTY:
        {
          lastProp = cmd.getProperty();
          stop = true;
          break;
        }
      }
    }
    if (cur != null)
    {
      result.add(cur);
    }
    return result;
  }
  
  private double dist(Point a, Point b)
  {
    return Math.sqrt((a.y-b.y)*(a.y-b.y)+(a.x-b.x)*(a.x-b.x));
  }
  
  private List<Element> sort(List<Element> e)
  {
    List<Element> result = new LinkedList<Element>();
    switch (strategy)
    {
      case FILE:
      {
        result.addAll(e);
        break;
      }
      case NEAREST:
      {
        result.add(e.remove(0));
        while (!e.isEmpty())
        {
          Point end = result.get(result.size()-1).getEnd();
          //find next
          int next = 0;
          double dst = -1;
          for (int i = 1; i < e.size(); i++)
          {
            double nd = dist(e.get(i).getEnd(), end);
            if (nd < dst || dst == -1)
            {
              next = i;
              dst = nd;
            }
          }
          //add next
          result.add(e.remove(next));
        }
        break;
      }
    }
    return result;
  }
  
  public VectorPart optimize(VectorPart vp)
  {
    List<Element> opt = this.sort(this.divide(vp));
    LaserProperty cp = opt.get(0).prop;
    VectorPart result = new VectorPart(opt.get(0).prop, vp.getDPI());
    for (Element e : opt)
    {
      if (!e.prop.equals(cp))
      {
        result.setProperty(e.prop);
        cp = e.prop;
      }
      result.moveto(e.start.x, e.start.y);
      for (Point p : e.moves)
      {
        result.lineto(p.x, p.y);
      }
    }
    return result;
  }
  
}
