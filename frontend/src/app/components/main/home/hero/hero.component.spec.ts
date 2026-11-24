/**
 * ============================================================================
 * 檔案名稱: hero.component.spec.ts
 * 測試對象: HeroComponent (首頁示範問卷：選行動裝置才解鎖子題、評分、送出與重設)
 * ============================================================================
 */

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HeroComponent } from './hero.component';

describe('HeroComponent', () => {
  let component: HeroComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HeroComponent], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(HeroComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('選行動裝置後可選作業系統；改回桌機時清除作業系統', () => {
    component.selectDevice('mobile');
    component.selectOs('ios');
    expect(component.selectedOs()).toBe('ios');
    expect(component.hasInteracted()).toBeTrue();

    component.selectDevice('desktop');
    expect(component.selectedOs()).toBeNull();
  });

  it('評分、送出後可重設回初始狀態', () => {
    component.selectRating(3);
    component.submitDemo();
    expect(component.isSubmitted()).toBeTrue();

    component.resetDemo();

    expect(component.selectedRating()).toBe(5);
    expect(component.isSubmitted()).toBeFalse();
    expect(component.hasInteracted()).toBeFalse();
    expect(component.selectedDevice()).toBe('desktop');
  });
});
