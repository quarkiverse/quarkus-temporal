package io.quarkiverse.temporal.it.cdi.defaultWorker;

import jakarta.inject.Inject;

import io.quarkiverse.temporal.it.cdi.CdiBean;
import io.quarkiverse.temporal.it.cdi.shared.CDIActivity;

public class CDIActivityImpl implements CDIActivity {

    @Inject
    CdiBean cdiBean;

    @Override
    public void cdi() {
        cdiBean.someMethod();

    }
}
