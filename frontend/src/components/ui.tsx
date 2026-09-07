import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'

type ButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'color'> & {
  color?: 'primary' | 'danger' | 'light' | 'dark'
  variant?: 'fill' | 'weak'
  display?: 'inline' | 'block' | 'full'
  size?: 'small' | 'medium' | 'large' | 'xlarge'
  loading?: boolean
}

export function Button({
  children,
  className = '',
  color = 'primary',
  variant = 'fill',
  display = 'inline',
  size = 'xlarge',
  loading = false,
  disabled,
  ...props
}: ButtonProps) {
  return (
    <button
      className={`ui-button ${className}`.trim()}
      data-color={color}
      data-variant={variant}
      data-display={display}
      data-size={size}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <span className="ui-spinner" aria-hidden="true" /> : null}
      <span>{children}</span>
    </button>
  )
}

type TextFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'prefix'> & {
  variant?: 'box' | 'line' | 'big' | 'hero'
  label?: string
  labelOption?: 'appear' | 'sustain'
  help?: ReactNode
  hasError?: boolean
}

export function TextField({
  id,
  label,
  labelOption = 'appear',
  help,
  hasError = false,
  className = '',
  value,
  ...props
}: TextFieldProps) {
  const showLabel = labelOption === 'sustain' || (value !== undefined && String(value).length > 0)
  return (
    <div className={`ui-text-field ${className}`.trim()} data-error={hasError}>
      {label && showLabel ? <label htmlFor={id}>{label}</label> : null}
      <input id={id} value={value} aria-invalid={hasError || undefined} {...props} />
      {help ? <div className="ui-field-help">{help}</div> : null}
    </div>
  )
}
