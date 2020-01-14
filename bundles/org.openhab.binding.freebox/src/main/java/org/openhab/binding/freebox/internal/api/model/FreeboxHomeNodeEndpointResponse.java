package org.openhab.binding.freebox.internal.api.model;

import org.openhab.binding.freebox.internal.api.FreeboxException;

public class FreeboxHomeNodeEndpointResponse extends FreeboxResponse<FreeboxHomeNodeEndpoint> {
    @Override
    public void evaluate() throws FreeboxException {
        super.evaluate();
        if (getResult() == null) {
            throw new FreeboxException("Missing result data in HOME configuration API response", this);
        }
    }
}