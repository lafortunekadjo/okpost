import { Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  template: `
    <span class="logo" [style.font-size.px]="taille()">
      <svg viewBox="0 0 32 32" [attr.width]="taille() * 1.4" [attr.height]="taille() * 1.4" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill="#15803d" />
        <path d="M9 16.5l4.5 4.5L23 11.5" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      <span><b>Ok</b>Post</span>
    </span>
  `,
  styles: `
    .logo { display: inline-flex; align-items: center; gap: 8px; color: var(--encre); font-weight: 500; letter-spacing: -.02em; }
    b { color: var(--vert); font-weight: 800; }
  `,
})
export class LogoComponent {
  taille = input(18);
}
