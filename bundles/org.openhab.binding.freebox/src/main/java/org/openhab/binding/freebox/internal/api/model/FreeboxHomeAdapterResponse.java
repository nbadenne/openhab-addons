package org.openhab.binding.freebox.internal.api.model;

import java.util.List;

import org.openhab.binding.freebox.internal.api.FreeboxException;

public class FreeboxHomeAdapterResponse extends FreeboxResponse<List<FreeboxHomeAdapter>> {
    @Override
    public void evaluate() throws FreeboxException {
        super.evaluate();
        if (getResult() == null) {
            throw new FreeboxException("Missing result data in HOME configuration API response", this);
        }
    }
}