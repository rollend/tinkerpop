package com.tinkerpop.gremlin.tinkergraph.structure;

import com.tinkerpop.gremlin.process.graph.GraphTraversal;
import com.tinkerpop.gremlin.structure.Direction;
import com.tinkerpop.gremlin.structure.Edge;
import com.tinkerpop.gremlin.structure.Element;
import com.tinkerpop.gremlin.structure.Property;
import com.tinkerpop.gremlin.structure.Vertex;
import com.tinkerpop.gremlin.structure.util.ElementHelper;
import com.tinkerpop.gremlin.structure.util.StringFactory;
import com.tinkerpop.gremlin.tinkergraph.process.graph.TinkerElementTraversal;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class TinkerEdge extends TinkerElement implements Edge {

    protected final Vertex inVertex;
    protected final Vertex outVertex;

    protected TinkerEdge(final Object id, final Vertex outVertex, final String label, final Vertex inVertex, final TinkerGraph graph) {
        super(id, label, graph);
        this.outVertex = outVertex;
        this.inVertex = inVertex;
        this.graph.edgeIndex.autoUpdate(Element.LABEL, this.label, null, this);
    }

    @Override
    public <V> Property<V> property(final String key, final V value) {
        if (TinkerHelper.inComputerMode(this.graph)) {
            return this.graph.graphView.setProperty(this, key, value);
        } else {
            ElementHelper.validateProperty(key, value);
            final Property oldProperty = super.property(key);
            final Property<V> newProperty = new TinkerProperty<>(this, key, value);
            this.properties.put(key, Arrays.asList(newProperty));
            this.graph.edgeIndex.autoUpdate(key, value, oldProperty.isPresent() ? oldProperty.value() : null, this);
            return newProperty;
        }
    }


    @Override
    public void remove() {
        final TinkerVertex outVertex = (TinkerVertex) this.outVertex;
        final TinkerVertex inVertex = (TinkerVertex) this.inVertex;

        if (null != outVertex && null != outVertex.outEdges) {
            final Set<Edge> edges = outVertex.outEdges.get(this.label());
            if (null != edges)
                edges.remove(this);
        }
        if (null != inVertex && null != inVertex.inEdges) {
            final Set<Edge> edges = inVertex.inEdges.get(this.label());
            if (null != edges)
                edges.remove(this);
        }

        this.graph.edgeIndex.removeElement(this);
        this.graph.edges.remove(this.id());
        this.properties.clear();
    }

    @Override
    public GraphTraversal<Edge, Edge> start() {
        return new TinkerElementTraversal<>(this, this.graph);
    }

    @Override
    public String toString() {
        return StringFactory.edgeString(this);

    }

    @Override
    public Edge.Iterators iterators() {
        return this.iterators;
    }

    private final Edge.Iterators iterators = new Iterators();

    protected class Iterators extends TinkerElement.Iterators implements Edge.Iterators {

        @Override
        public Iterator<Vertex> vertices(final Direction direction) {
            return (Iterator) TinkerHelper.getVertices(TinkerEdge.this, direction);
        }

        @Override
        public <V> Iterator<Property<V>> properties(final String... propertyKeys) {
            return (Iterator) super.properties(propertyKeys);
        }

        @Override
        public <V> Iterator<Property<V>> hiddens(final String... propertyKeys) {
            return (Iterator) super.hiddens(propertyKeys);
        }
    }
}
