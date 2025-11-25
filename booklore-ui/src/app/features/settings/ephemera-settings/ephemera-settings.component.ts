import {Component} from '@angular/core';
import {EphemeraSettingsComponent as EphemeraSettingsComponentInner} from '../device-settings/component/ephemera-settings/ephemera-settings-component';

@Component({
  selector: 'app-ephemera-settings',
  standalone: true,
  imports: [EphemeraSettingsComponentInner],
  template: `
    <div class="w-full h-[calc(100dvh-10.5rem)] md:h-[calc(100dvh-11.65rem)] overflow-y-auto border rounded-lg enclosing-container">
      <app-ephemera-settings-component></app-ephemera-settings-component>
    </div>
  `,
  styleUrl: './ephemera-settings.component.scss'
})
export class EphemeraSettingsComponent {
}
