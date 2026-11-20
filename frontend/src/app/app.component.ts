import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmationComponent } from './shared/confirmation.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ConfirmationComponent],
  template: '<router-outlet /><app-confirmation />',
})
export class AppComponent {}
