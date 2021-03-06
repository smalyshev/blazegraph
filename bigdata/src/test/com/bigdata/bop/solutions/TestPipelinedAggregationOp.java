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
package com.bigdata.bop.solutions;

import com.bigdata.bop.BOp;
import com.bigdata.bop.BOpEvaluationContext;
import com.bigdata.bop.IConstraint;
import com.bigdata.bop.IValueExpression;
import com.bigdata.bop.IVariable;
import com.bigdata.bop.IVariableFactory;
import com.bigdata.bop.NV;
import com.bigdata.bop.PipelineOp;

/**
 * Unit tests for {@link PipelinedAggregationOp}.
 * 
 * @author thompsonbry
 */
public class TestPipelinedAggregationOp extends AbstractAggregationTestCase {
    
	public TestPipelinedAggregationOp() {
	}

	public TestPipelinedAggregationOp(String name) {
		super(name);
	}

    @Override
    protected GroupByOp newFixture(IValueExpression<?>[] select,
            IValueExpression<?>[] groupBy, IConstraint[] having) {

        final int groupById = 1;

        final IVariableFactory variableFactory = new MockVariableFactory();

        final IGroupByState groupByState = new GroupByState(//
                select, groupBy, having);

        final IGroupByRewriteState groupByRewrite = new GroupByRewriter(
                groupByState) {

            private static final long serialVersionUID = 1L;

            @Override
            public IVariable<?> var() {
                return variableFactory.var();
            }

        };

        final GroupByOp query = new PipelinedAggregationOp(new BOp[] {},
                NV.asMap(new NV[] {//
                        new NV(BOp.Annotations.BOP_ID, groupById),//
                        new NV(BOp.Annotations.EVALUATION_CONTEXT,
                                BOpEvaluationContext.CONTROLLER),//
                        new NV(PipelineOp.Annotations.PIPELINED, true),//
                        new NV(PipelineOp.Annotations.MAX_PARALLEL, 1),//
                        new NV(PipelineOp.Annotations.SHARED_STATE, true),//
                        new NV(PipelineOp.Annotations.LAST_PASS, true),//
                        new NV(GroupByOp.Annotations.GROUP_BY_STATE, groupByState), //
                        new NV(GroupByOp.Annotations.GROUP_BY_REWRITE, groupByRewrite), //
                }));

        return query;
    }

    @Override
    protected boolean isPipelinedAggregationOp() {

        return true;
        
    }

}
