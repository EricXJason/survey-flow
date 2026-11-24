/**
 * ============================================================================
 * 檔案名稱: app.routes.ts
 * 所屬模組: 應用程式路由導航模組 (資安 S 級：路由守衛)
 * 責任描述: 定義全站頁面路由，以及兩個守衛：
 *   - authGuard：未登入者進入問卷設計、問卷管理、會員管理、個人資料頁時，跳出提示並導向 /log-in
 *   - canDeactivateGuard：離開頁面前呼叫元件之 canDeactivate()，有未儲存變更時先確認
 * 架構分層: Routing Layer
 * 聯動關係:
 *   - 上游依賴: UserStateService (是否登入)、DialogService (提示)、各頁面元件 (延遲載入)
 *   - 下游消費: app.config.ts (provideRouter)
 * 邊界處理: [Why]: 前端守衛只負責使用體驗 (避免看到無法使用的頁面)，不是安全防線；
 *           即使繞過守衛，後端 SecurityConfig 仍會對未登入請求回 401、權限不足回 403。
 *           未匹配之網址導向 not-found 頁。
 * ============================================================================
 */

import { CanActivateFn, CanDeactivateFn, Routes } from '@angular/router';
import { Observable } from 'rxjs';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { UserStateService } from './@services/user-state.service';
import { DialogService } from './@services/dialog.service';

/**
 * 具備離開前確認能力之組件介面 (CanComponentDeactivate)
 */
export interface CanComponentDeactivate {
  canDeactivate: () => Observable<boolean> | Promise<boolean> | boolean;
}

/**
 * 路由離開守衛 (CanDeactivate Guard)
 * 攔截跳轉導航，於離開前呼叫組件之 canDeactivate 方法（如彈出確認對話框）。
 * 只有使用者明確確認離開時才放行導航；取消時由 Angular Router 取消跳轉並維持原地狀態，
 * 避免先跳轉導致表單填答進度遺失或頁面閃爍跳躍。
 */
export const canDeactivateGuard: CanDeactivateFn<CanComponentDeactivate> = (
  component: CanComponentDeactivate,
) => {
  return component?.canDeactivate ? component.canDeactivate() : true;
};

/**
 * 會員登入守衛 (Auth Guard)
 * 嚴格阻斷未登入之訪客進入問卷設計器與管理後臺，未登入者僅允許填答與瀏覽大廳
 */
export const authGuard: CanActivateFn = () => {
  const userState = inject(UserStateService);
  const router = inject(Router);
  const dialogService = inject(DialogService);

  if (userState.isLoggedIn()) {
    return true;
  }

  dialogService.showDialog({
    title: '請先登入會員',
    message: '您目前為訪客身分，必須先登入會員帳號方可發起或設計問卷。',
    mode: 'errorMode',
  });
  return router.createUrlTree(['/log-in']);
};

export const routes: Routes = [
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  {
    path: 'home',
    loadComponent: () =>
      import('./components/main/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'lobby',
    loadComponent: () =>
      import('./components/main/lobby/lobby.component').then((m) => m.LobbyComponent),
  },
  {
    path: 'survey-runner',
    loadComponent: () =>
      import('./components/main/survey-runner/survey-runner.component').then(
        (m) => m.SurveyRunnerComponent,
      ),
    canDeactivate: [canDeactivateGuard],
  },
  {
    path: 'log-in',
    loadComponent: () =>
      import('./components/main/log-in/log-in.component').then((m) => m.LogInComponent),
  },
  {
    path: 'forget-password',
    loadComponent: () =>
      import('./components/main/forget-password/forget-password.component').then(
        (m) => m.ForgetPasswordComponent,
      ),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./components/main/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./components/main/reset-password/reset-password.component').then(
        (m) => m.ResetPasswordComponent,
      ),
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./components/main/verify-email/verify-email.component').then(
        (m) => m.VerifyEmailComponent,
      ),
  },
  {
    path: 'survey-design',
    loadComponent: () =>
      import('./components/main/survey-design/survey-design.component').then(
        (m) => m.SurveyDesignComponent,
      ),
    canActivate: [authGuard],
    canDeactivate: [canDeactivateGuard],
  },
  {
    path: 'survey-management',
    loadComponent: () =>
      import('./components/main/survey-management/survey-management.component').then(
        (m) => m.SurveyManagementComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'user-management',
    loadComponent: () =>
      import('./components/main/user-management/user-management.component').then(
        (m) => m.UserManagementComponent,
      ),
    canActivate: [authGuard],
  },
  {
    path: 'user-profile',
    loadComponent: () =>
      import('./components/main/user-profile/user-profile.component').then(
        (m) => m.UserProfileComponent,
      ),
    canActivate: [authGuard],
    canDeactivate: [canDeactivateGuard],
  },
  {
    path: 'not-found',
    loadComponent: () =>
      import('./components/main/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
  {
    path: '**',
    loadComponent: () =>
      import('./components/main/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
];
