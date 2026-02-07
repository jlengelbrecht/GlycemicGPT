/**
 * Tests for the main page component.
 * Story 1.1 AC: Web UI accessible at localhost:3000
 */

import { render, screen } from '@testing-library/react';
import Home from '../src/app/page';

describe('Home Page', () => {
  it('renders the GlycemicGPT heading', () => {
    render(<Home />);
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading).toHaveTextContent('GlycemicGPT');
  });

  it('renders the tagline', () => {
    render(<Home />);
    expect(screen.getByText('Your on-call endo at home')).toBeInTheDocument();
  });

  it('renders the Get Started link', () => {
    render(<Home />);
    const link = screen.getByRole('link', { name: /get started/i });
    expect(link).toHaveAttribute('href', '/login');
  });
});
