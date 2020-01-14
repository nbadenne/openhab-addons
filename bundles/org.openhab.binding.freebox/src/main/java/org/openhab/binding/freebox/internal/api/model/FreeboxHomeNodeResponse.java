package org.openhab.binding.freebox.internal.api.model;

import org.openhab.binding.freebox.internal.api.FreeboxException;

public class FreeboxHomeNodeResponse extends FreeboxResponse<FreeboxHomeNode> {
    @Override
    public void evaluate() throws FreeboxException {
        super.evaluate();
        if (getResult() == null) {
            throw new FreeboxException("Missing result data in HOME configuration API response", this);
        }
    }
}