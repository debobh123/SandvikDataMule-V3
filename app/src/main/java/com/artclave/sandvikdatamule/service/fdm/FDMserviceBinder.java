package com.artclave.sandvikdatamule.service.fdm;

import android.os.Binder;

public class FDMserviceBinder extends Binder {
    FDMservice service;

    public FDMserviceBinder(FDMservice service) {
        this.service = service;
    }
}
