package com.mythosis.beandiff;

/**
 * Implementing this interface allows an object to resolve data, given a certain input.
 *
 * @author juramirez@softekpr.com
 */
public interface DataResolver<I, O> {
    /**
     * Resolves data given a parameter.
     * <p/>
     * This method is responsible for resolving data given a certain input.
     * For example, it could look up a an <code>id</code> in a database and return
     * the results.
     *
     * @param param the input data to use for resolution
     * @return the resolved data
     */
    public O resolve(I param);
}
