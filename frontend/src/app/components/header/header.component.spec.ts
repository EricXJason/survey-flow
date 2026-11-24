/**
 * ============================================================================
 * 檔案名稱: header.component.spec.ts
 * 測試對象: HeaderComponent (依登入身分顯示導覽、手機選單開關、Esc 與視窗放大自動收合、登出)
 * ============================================================================
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { UserProfileData } from '../../@interfaces/account-model';
import { UserEmitterService } from '../../@services/user-emitter.service';
import { UserStateService } from '../../@services/user-state.service';
import { HeaderComponent } from './header.component';

describe('HeaderComponent', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let component: HeaderComponent;
  let userState: UserStateService;
  let emitter: jasmine.SpyObj<UserEmitterService>;

  /**
   * 以指定權限登入
   * @param access 權限 (member / admin)
   * @param name 顯示名稱
   */
  const loginAs = (access: string, name = '王小明'): void => {
    userState.setIdentity({ id: '1', access, userName: 'u1', name, nationalId: '', email: '', phone: '' } as UserProfileData);
    fixture.detectChanges();
  };

  /**
   * 取得導覽列所有連結文字
   * @returns 連結文字陣列
   */
  const navTexts = (): string[] =>
    Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.header-nav .nav-link')).map((a) => a.textContent!.trim());

  beforeEach(async () => {
    sessionStorage.clear();
    emitter = jasmine.createSpyObj('UserEmitterService', ['emitLogout']);
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [provideRouter([]), { provide: UserEmitterService, useValue: emitter }],
    }).compileComponents();
    userState = TestBed.inject(UserStateService);
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => sessionStorage.clear());

  it('訪客只看到首頁與問卷大廳', () => {
    expect(component.isLoggedIn()).toBeFalse();
    expect(navTexts()).toEqual(['首頁', '問卷大廳']);
  });

  it('會員多看到問卷管理；管理員再多看到會員管理', () => {
    loginAs('member');
    expect(navTexts()).toEqual(['首頁', '問卷大廳', '問卷管理']);
    expect(component.userRole()).toBe('一般會員');
    expect(component.userInitial()).toBe('王');

    loginAs('admin');
    expect(navTexts()).toEqual(['首頁', '問卷大廳', '問卷管理', '會員管理']);
    expect(component.userRole()).toBe('系統管理員');
  });

  it('沒有姓名時顯示帳號；帳號也沒有時頭像顯示 U', () => {
    loginAs('member', '');
    expect(component.userName()).toBe('u1');

    userState.userName.set('');
    expect(component.userInitial()).toBe('U');
  });

  it('漢堡按鈕切換手機選單；Esc 關閉 (其他按鍵不影響)', () => {
    component.toggleMobileMenu();
    expect(component.isMobileMenuOpen()).toBeTrue();

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(component.isMobileMenuOpen()).toBeTrue();

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(component.isMobileMenuOpen()).toBeFalse();
  });

  it('視窗放大到桌機寬度 (> 920px) 時自動收合手機選單', () => {
    component.toggleMobileMenu();
    spyOnProperty(window, 'innerWidth').and.returnValue(1200);

    window.dispatchEvent(new Event('resize'));

    expect(component.isMobileMenuOpen()).toBeFalse();
  });

  it('登出：收合選單並委派 UserEmitterService', () => {
    component.toggleMobileMenu();

    component.onLogout();

    expect(component.isMobileMenuOpen()).toBeFalse();
    expect(emitter.emitLogout).toHaveBeenCalled();
  });
});
