/**
 * ============================================================================
 * 檔案名稱: home.component.spec.ts
 * 測試對象: HomeComponent (題型輪播自動切換與暫停、手動切換、各題型示範作答與重設)
 * ============================================================================
 */

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let component: HomeComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HomeComponent], providers: [provideRouter([])] }).compileComponents();
    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
  });

  it('輪播每 5 秒切換一個題型，暫停時不切換，最後一個之後回到第一個', fakeAsync(() => {
    fixture.detectChanges();

    tick(5000);
    expect(component.activeTabIndex()).toBe(1);

    component.pauseWheel();
    tick(5000);
    expect(component.activeTabIndex()).toBe(1);

    component.resumeWheel();
    component.selectTab(5);
    tick(5000);
    expect(component.activeTabIndex()).toBe(0);
    fixture.destroy();
  }));

  it('手動切換：上一個 / 下一個循環；超出範圍的索引忽略', () => {
    component.prevTab();
    expect(component.activeTabIndex()).toBe(5);
    component.nextTab();
    expect(component.activeTabIndex()).toBe(0);

    component.selectTab(99);
    component.selectTab(-1);
    expect(component.activeTabIndex()).toBe(0);
    component.stopWheel();
  });

  it('示範作答：是非、跳題、複選切換、量表、文字與日期時間', () => {
    component.setBinaryAnswer('no');
    component.setJumpDevice('mobile');
    component.setJumpScreen('6-inch');
    component.toggleMultiOption('charts');
    component.toggleMultiOption('share');
    component.setScaleRating(2);
    component.fillSampleText('好用');
    component.onTextInput({ target: { value: '很好用' } } as unknown as Event);
    component.setDateTimePreset('2026-12-01', '09:00');
    component.onDateChange({ target: { value: '2026-12-02' } } as unknown as Event);
    component.onTimeChange({ target: { value: '10:30' } } as unknown as Event);
    component.onTextInput({ target: null } as unknown as Event);

    expect(component.demoBinary()).toBe('no');
    expect(component.demoJumpDevice()).toBe('mobile');
    expect(component.demoJumpScreen()).toBe('6-inch');
    expect(component.demoMulti()).toEqual(['export', 'share']);
    expect(component.isMultiSelected('charts')).toBeFalse();
    expect(component.demoScale()).toBe(2);
    expect(component.demoText()).toBe('很好用');
    expect([component.demoDate(), component.demoTime()]).toEqual(['2026-12-02', '10:30']);
  });

  it('重設目前題型的示範答案', () => {
    const resetAt = (index: number): void => {
      component.selectTab(index);
      component.resetCurrentDemo();
    };

    resetAt(0);
    resetAt(1);
    resetAt(2);
    resetAt(3);
    resetAt(4);
    resetAt(5);
    component.stopWheel();

    expect(component.demoBinary()).toBeNull();
    expect([component.demoJumpDevice(), component.demoJumpScreen()]).toEqual(['mobile', '']);
    expect(component.demoMulti()).toEqual([]);
    expect(component.demoScale()).toBe(3);
    expect(component.demoText()).toBe('');
    expect([component.demoDate(), component.demoTime()]).toEqual(['2026-10-21', '10:00']);
  });
});
