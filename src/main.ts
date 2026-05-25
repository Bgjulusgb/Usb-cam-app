import './styles/app.css';
import { App } from './App';

const root = document.getElementById('app');
if (!root) throw new Error('#app not found');

const app = new App();
app.init(root).catch(console.error);
