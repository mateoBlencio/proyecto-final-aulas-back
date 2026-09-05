package ar.edu.utn.frc.siga.sysacad.api;

public interface SysacadViewSyncer {

    SysacadView view();

    void sync(SysacadCatalogReader catalog);
}
