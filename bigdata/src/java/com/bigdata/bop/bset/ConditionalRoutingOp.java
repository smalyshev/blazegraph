/**

Copyright (C) SYSTAP, LLC 2006-2015.  All rights reserved.

Contact:
     SYSTAP, LLC
     2501 Calvert ST NW #106
     Washington, DC 20008
     licenses@systap.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
/*
 * Created on Aug 25, 2010
 */

package com.bigdata.bop.bset;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import com.bigdata.bop.BOp;
import com.bigdata.bop.BOpContext;
import com.bigdata.bop.IBindingSet;
import com.bigdata.bop.IConstraint;
import com.bigdata.bop.NV;
import com.bigdata.bop.PipelineOp;
import com.bigdata.bop.engine.BOpStats;
import com.bigdata.relation.accesspath.IBlockingBuffer;

import cutthecrap.utils.striterators.ICloseableIterator;

/**
 * An operator for conditional routing of binding sets in a pipeline. The
 * operator will copy binding sets either to the default sink (if a condition is
 * satisfied) and otherwise to the alternate sink (iff one is specified). If a
 * solution fails the constraint and the alternate sink is not specified, then
 * the solution is dropped.
 * <p>
 * Conditional routing can be useful where a different data flow is required
 * based on the type of an object (for example a term identifier versus an
 * inline term in the RDF database) or where there is a need to jump around a
 * join group based on some condition.
 * <p>
 * Conditional routing will cause reordering of solutions when the alternate
 * sink is specified as some solutions will flow to the primary sink while
 * others flow to the alterate sink.
 * 
 * @author <a href="mailto:thompsonbry@users.sourceforge.net">Bryan Thompson</a>
 * @version $Id: ConditionalRoutingOp.java 7773 2014-01-11 12:49:05Z thompsonbry
 *          $
 */
public class ConditionalRoutingOp extends PipelineOp {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public interface Annotations extends PipelineOp.Annotations {

        /**
         * An {@link IConstraint} which specifies the condition. When the
         * condition is satisfied the binding set is routed to the default sink.
         * When the condition is not satisfied, the binding set is routed to the
         * alternative sink.
         */
        String CONDITION = ConditionalRoutingOp.class.getName() + ".condition";

    }

    /**
     * Deep copy constructor.
     * 
     * @param op
     */
    public ConditionalRoutingOp(final ConditionalRoutingOp op) {
        
        super(op);
        
    }

    /**
     * Shallow copy constructor.
     * 
     * @param args
     * @param annotations
     */
    public ConditionalRoutingOp(final BOp[] args,
            final Map<String, Object> annotations) {

        super(args, annotations);

    }

    public ConditionalRoutingOp(final BOp[] args, final NV... anns) {

        this(args, NV.asMap(anns));

    }

    /**
     * @see Annotations#CONDITION
     */
    public IConstraint getCondition() {
        
        return (IConstraint) getProperty(Annotations.CONDITION);
        
    }
    
    @Override
    public FutureTask<Void> eval(final BOpContext<IBindingSet> context) {

        return new FutureTask<Void>(new ConditionalRouteTask(this, context));
        
    }

    /**
     * Copy the source to the sink or the alternative sink depending on the
     * condition.
     */
    static private class ConditionalRouteTask implements Callable<Void> {

        private final BOpStats stats;

        private final IConstraint condition;
        
        private final ICloseableIterator<IBindingSet[]> source;

        private final IBlockingBuffer<IBindingSet[]> sink;
        
        private final IBlockingBuffer<IBindingSet[]> sink2;

        ConditionalRouteTask(final ConditionalRoutingOp op,
                final BOpContext<IBindingSet> context) {

            this.stats = context.getStats();
            
            this.condition = op.getCondition();

            if (condition == null)
                throw new IllegalArgumentException();
            
            this.source = context.getSource();
            
            this.sink = context.getSink();

            this.sink2 = context.getSink2(); // MAY be null.

//            if (sink2 == null)
//                throw new IllegalArgumentException();
            
            if (sink == sink2)
                throw new IllegalArgumentException();

        }

        @Override
        public Void call() throws Exception {
            try {
                while (source.hasNext()) {
                    
                    final IBindingSet[] chunk = source.next();
                    
                    stats.chunksIn.increment();
                    stats.unitsIn.add(chunk.length);

                    final IBindingSet[] def = new IBindingSet[chunk.length];
                    final IBindingSet[] alt = sink2 == null ? null
                            : new IBindingSet[chunk.length];

                    int ndef = 0, nalt = 0;

                    for (int i = 0; i < chunk.length; i++) {

                        if (i % 20 == 0 && Thread.interrupted()) {

                            // Eagerly notice if the operator is interrupted.
                            throw new RuntimeException(
                                    new InterruptedException());

                        }

                        final IBindingSet bset = chunk[i].clone();

                        if (condition.accept(bset)) {

                            // solution passes condition. default sink.
                            def[ndef++] = bset;

                        } else if (sink2 != null) {

                            // solution fails condition. alternative sink.
                            alt[nalt++] = bset;

                        }

                   }

                    if (ndef > 0) {
                        if (ndef == def.length)
                            sink.add(def);
                        else
                            sink.add(Arrays.copyOf(def, ndef));
//                        stats.chunksOut.increment();
//                        stats.unitsOut.add(ndef);
                    }

                    if (nalt > 0 && sink2 != null) {
                        if (nalt == alt.length)
                            sink2.add(alt);
                        else
                            sink2.add(Arrays.copyOf(alt, nalt));
//                        stats.chunksOut.increment();
//                        stats.unitsOut.add(nalt);
                    }
                    
                }

                sink.flush();
                if (sink2 != null)
                    sink2.flush();

                return null;

            } finally {
                source.close();
                sink.close();
                if (sink2 != null)
                    sink2.close();

            }

        } // call()

    } // ConditionalRoutingTask.

}
